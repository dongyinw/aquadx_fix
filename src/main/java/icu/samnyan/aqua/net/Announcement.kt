package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity
@Table(name = "net_announcement")
class NetAnnouncement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var title: String = "",
    @Column(columnDefinition = "TEXT")
    var content: String = "",
    /** info | warning | danger, only changes how the notice is coloured. */
    var level: String = "info",
    var enabled: Boolean = true,
    /** yyyy-MM-dd HH:mm:ss in Beijing time; empty means no limit. */
    var startDate: String = "",
    var endDate: String = "",
    var createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun isActive(now: String) = enabled && (startDate.isBlank() || now >= startDate) && (endDate.isBlank() || now <= endDate)
}

interface NetAnnouncementRepo : JpaRepository<NetAnnouncement, Long>

data class AnnouncementInput(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val level: String = "info",
    val enabled: Boolean = true,
    val startDate: String = "",
    val endDate: String = "",
)

@Service
class AnnouncementService(private val repo: NetAnnouncementRepo) {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
        val LEVELS = setOf("info", "warning", "danger")

        fun now(): String = LocalDateTime.now(ZONE).format(DATE_FORMAT)
    }

    private fun view(a: NetAnnouncement, now: String) = mapOf(
        "id" to a.id,
        "title" to a.title,
        "content" to a.content,
        "level" to a.level,
        "enabled" to a.enabled,
        "startDate" to a.startDate,
        "endDate" to a.endDate,
        "active" to a.isActive(now),
        "updatedAt" to a.updatedAt.format(DATE_FORMAT),
    )

    /** What players see: enabled and inside their time window, newest first. */
    @Transactional(readOnly = true)
    fun active(now: String = now()) = repo.findAll().filter { it.isActive(now) }.sortedByDescending { it.id }.take(5).map { view(it, now) }

    @Transactional(readOnly = true)
    fun all(now: String = now()) = repo.findAll().sortedByDescending { it.id }.map { view(it, now) }

    @Transactional
    fun save(input: AnnouncementInput): Map<String, Any?> {
        val title = input.title.trim()
        val content = input.content.trim()
        if (title.isEmpty() || title.length > 100) 400 - "标题需要 1 到 100 个字符"
        if (content.isEmpty() || content.length > 2000) 400 - "内容需要 1 到 2000 个字符"
        if (input.level !in LEVELS) 400 - "公告级别不正确"
        for (date in listOf(input.startDate, input.endDate)) {
            if (date.isNotBlank() && !DATE_REGEX.matches(date)) 400 - "时间格式需要是 yyyy-MM-dd HH:mm:ss"
        }
        if (input.startDate.isNotBlank() && input.endDate.isNotBlank() && input.endDate < input.startDate) 400 - "结束时间不能早于开始时间"

        val entity = if (input.id != 0L) repo.findById(input.id).orElse(null) ?: (404 - "公告不存在") else NetAnnouncement()
        entity.title = title
        entity.content = content
        entity.level = input.level
        entity.enabled = input.enabled
        entity.startDate = input.startDate
        entity.endDate = input.endDate
        entity.updatedAt = LocalDateTime.now()
        return view(repo.save(entity), now())
    }

    @Transactional
    fun delete(id: Long) {
        if (!repo.existsById(id)) 404 - "公告不存在"
        repo.deleteById(id)
    }
}

@RestController
@API("/api/v2/announcement")
class AnnouncementApi(private val service: AnnouncementService) {
    @API("/list")
    @Doc("Announcements that are currently shown to players.", "Announcements")
    fun list() = service.active()
}

@RestController
@API("/api/v2/admin/announcement")
class AnnouncementAdminApi(private val jwt: JWT, private val service: AnnouncementService) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("/list")
    @Doc("All announcements including disabled and expired ones.", "Announcements")
    fun list(@RP token: Str) = admin(token).let { service.all() }

    @API("/save")
    @Doc("Create (id = 0) or update an announcement.", "Announcement")
    fun save(@RP token: Str, @RB body: AnnouncementInput) = admin(token).let { service.save(body) }

    @API("/delete")
    @Doc("Delete an announcement.", "Success")
    fun delete(@RP token: Str, @RP id: Long) = admin(token).let { service.delete(id); mapOf("success" to true) }
}
