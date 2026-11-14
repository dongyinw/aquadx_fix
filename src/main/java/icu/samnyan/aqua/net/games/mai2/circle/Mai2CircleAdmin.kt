package icu.samnyan.aqua.net.games.mai2.circle

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Circle management for admins: browse every circle, edit it, remove members, hand over ownership or delete it. */
@Service
class Mai2CircleAdminService(
    private val mai2: Mai2Repos,
    private val circles: Mai2CircleRepo,
    private val members: Mai2CircleMemberRepo,
    private val requests: Mai2CircleJoinRequestRepo,
) {
    companion object {
        const val PAGE_SIZE = 20
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private fun circle(id: Long) = circles.findById(id).orElse(null) ?: (404 - "Circle not found")

    private fun playerName(extId: Long) = mai2.userData.findByCardExtId(extId)?.userName ?: ""

    private fun format(time: LocalDateTime) = time.format(DATE_FORMAT)

    private fun row(c: Mai2CircleEntity) = mapOf(
        "circleId" to c.id,
        "circleName" to c.circleName,
        "circleCode" to c.circleCode,
        "comment" to c.comment,
        "ownerExtId" to c.ownerExtId,
        "ownerName" to playerName(c.ownerExtId),
        "isPublic" to c.isPublic,
        "isAllowAnyoneJoin" to c.isAllowAnyoneJoin,
        "memberCount" to members.countByCircleId(c.id),
        "requestCount" to requests.countByCircleId(c.id),
        "createdAt" to format(c.createdAt),
    )

    @Transactional(readOnly = true)
    fun list(query: String, page: Int): CirclePage<Map<String, Any?>> {
        val p = page.coerceIn(0, 100_000)
        val result = circles.search(query.trim(), PageRequest.of(p, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
        return CirclePage(result.content.map(::row), result.totalElements, result.totalPages, p)
    }

    @Transactional(readOnly = true)
    fun members(circleId: Long): List<Map<String, Any?>> {
        val c = circle(circleId)
        return members.findByCircleIdOrderByPointDescJoinedAtAsc(c.id).map { m ->
            val user = mai2.userData.findByCardExtId(m.aimeExtId)
            mapOf(
                "extId" to m.aimeExtId,
                "userName" to (user?.userName ?: ""),
                "playerRating" to (user?.playerRating ?: 0),
                "point" to m.point,
                "joinedAt" to format(m.joinedAt),
                "lastLoginDate" to m.lastLoginDate,
                "isOwner" to (m.aimeExtId == c.ownerExtId),
            )
        }
    }

    @Transactional
    fun update(circleId: Long, name: String, comment: String, allowAnyoneJoin: Boolean): Map<String, Any?> {
        val c = circle(circleId)
        if (name.isBlank() || name.trim().length > 32) 400 - "圈子名称需要 1 到 32 个字符"
        c.circleName = name.trim()
        c.comment = comment.trim().take(500)
        c.isAllowAnyoneJoin = allowAnyoneJoin
        c.updatedAt = LocalDateTime.now()
        return row(circles.save(c))
    }

    @Transactional
    fun kick(circleId: Long, extId: Long) {
        val c = circle(circleId)
        if (extId == c.ownerExtId) 400 - "不能移除圈主，请先转让圈主或直接解散圈子"
        if (members.findByAimeExtId(extId)?.circleId != c.id) 404 - "该玩家不在这个圈子里"
        members.deleteByCircleIdAndAimeExtId(c.id, extId)
    }

    @Transactional
    fun setOwner(circleId: Long, extId: Long): Map<String, Any?> {
        val c = circle(circleId)
        if (members.findByAimeExtId(extId)?.circleId != c.id) 404 - "新圈主必须是圈子成员"
        c.ownerExtId = extId
        c.updatedAt = LocalDateTime.now()
        return row(circles.save(c))
    }

    @Transactional
    fun delete(circleId: Long) {
        // Members and join requests go with the circle (ON DELETE CASCADE).
        circles.delete(circle(circleId))
    }
}

@RestController
@API("/api/v2/admin/mai2-circle")
class Mai2CircleAdminApi(
    private val jwt: JWT,
    private val service: Mai2CircleAdminService,
) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("/list")
    @Doc("All circles, newest first. Optional search by name or circle code.", "Circle page")
    fun list(@RP token: Str, @RP(required = false) query: Str?, @RP(required = false) page: Int?) =
        admin(token).let { service.list(query ?: "", page ?: 0) }

    @API("/members")
    @Doc("Members of a circle ordered by points.", "Circle members")
    fun members(@RP token: Str, @RP circleId: Long) = admin(token).let { service.members(circleId) }

    @API("/update")
    @Doc("Change a circle's name, description and whether anyone may join directly.", "Circle")
    fun update(@RP token: Str, @RP circleId: Long, @RP name: Str, @RP(required = false) comment: Str?, @RP allowAnyoneJoin: Boolean) =
        admin(token).let { service.update(circleId, name, comment ?: "", allowAnyoneJoin) }

    @API("/kick")
    @Doc("Remove a member from a circle.", "Success")
    fun kick(@RP token: Str, @RP circleId: Long, @RP extId: Long) = admin(token).let { service.kick(circleId, extId); mapOf("success" to true) }

    @API("/owner")
    @Doc("Hand a circle over to another member.", "Circle")
    fun owner(@RP token: Str, @RP circleId: Long, @RP extId: Long) = admin(token).let { service.setOwner(circleId, extId) }

    @API("/delete")
    @Doc("Dissolve a circle.", "Success")
    fun delete(@RP token: Str, @RP circleId: Long) = admin(token).let { service.delete(circleId); mapOf("success" to true) }
}
