package icu.samnyan.aqua.net.games.mai2

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentDef
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService.Companion.now
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/** Tournament (KOP) leaderboards for the website, plus tournament management for admins. */
@RestController
@API("/api/v2/game/mai2/tournament")
class Mai2TournamentApi(
    val jwt: JWT,
    val tournaments: Mai2TournamentService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(Mai2TournamentApi::class.java)
        private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
    }

    private fun summary(t: Mai2TournamentDef, now: String) = mapOf(
        "id" to t.id,
        "name" to t.name,
        "enabled" to t.enabled,
        "active" to t.isActive(now),
        "expired" to t.isExpired(now),
        "rankingKind" to t.rankingKind,
        "scoreType" to t.scoreType,
        "noticeStartDate" to t.noticeStartDate,
        "noticeEndDate" to t.noticeEndDate,
        "startDate" to t.startDate,
        "endDate" to t.endDate,
        "entryStartDate" to t.entryStartDate,
        "entryEndDate" to t.entryEndDate,
        "musicIds" to t.musicIds,
        "lockedMusicIds" to t.lockedMusicIds,
    )

    private fun isAdmin(token: Str?) = token?.let { jwt.parse(it) }?.isAdmin == true

    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("list")
    @Doc("Tournaments that are currently running (enabled and not expired)")
    fun list(): List<Map<String, Any>> {
        val now = now()
        return tournaments.active(now).map { summary(it, now) }
    }

    @API("ranking")
    @Doc("Leaderboard of a tournament. Disabled tournaments are only visible to admins.")
    fun ranking(@RP id: Int, @RP token: Str?): Map<String, Any> {
        val t = tournaments.find(id)?.takeIf { it.enabled || isAdmin(token) } ?: (404 - "Tournament not found")
        return mapOf("tournament" to summary(t, now()), "ranking" to tournaments.leaderboard(t))
    }

    @API("admin/list")
    @Doc("All tournaments, including disabled and expired ones")
    fun adminList(@RP token: Str): List<Map<String, Any>> {
        admin(token)
        val now = now()
        return tournaments.all().map { summary(it, now) }
    }

    @API(value = ["admin/save"], method = [RequestMethod.POST])
    @Doc("Create or update a tournament")
    fun adminSave(@RP token: Str, @RB body: Mai2TournamentDef): Map<String, Any> {
        val actor = admin(token)
        if (body.id <= 0) 400 - "大赛 ID 必须为正整数"
        body.name = body.name.trim()
        if (body.name.isEmpty()) 400 - "请填写大赛名称"
        body.musicIds = body.musicIds.distinct()
        if (body.musicIds.isEmpty()) 400 - "请至少填写一首课题曲"
        if (body.musicIds.any { it <= 0 }) 400 - "曲目 ID 必须为正整数"
        body.lockedMusicIds = body.lockedMusicIds.distinct()
        if (!body.musicIds.containsAll(body.lockedMusicIds)) 400 - "锁定曲目必须是课题曲之一"

        listOf(body::noticeStartDate, body::noticeEndDate, body::startDate, body::endDate, body::entryStartDate, body::entryEndDate)
            .forEach { f ->
                val v = f.get().trim().also { f.set(it) }
                if (v.isNotEmpty() && !dateRegex.matches(v)) 400 - "日期格式应为 yyyy-MM-dd HH:mm:ss：$v"
            }
        if (body.startDate.isEmpty() || body.endDate.isEmpty()) 400 - "请填写开始和结束时间"
        if (body.startDate > body.endDate) 400 - "开始时间不能晚于结束时间"

        tournaments.repo.save(body.toEntity())
        tournaments.clearCache()
        log.info("Admin {} saved maimai2 tournament {}", actor.auId, body.id)
        return summary(body, now())
    }

    @API("admin/delete")
    @Doc("Delete a tournament. Playlogs are not affected.")
    fun adminDelete(@RP token: Str, @RP id: Int): Map<String, Any> {
        val actor = admin(token)
        if (!tournaments.repo.existsById(id)) 404 - "Tournament not found"
        tournaments.repo.deleteById(id)
        tournaments.clearCache()
        log.info("Admin {} deleted maimai2 tournament {}", actor.auId, id)
        return mapOf("success" to true)
    }
}
