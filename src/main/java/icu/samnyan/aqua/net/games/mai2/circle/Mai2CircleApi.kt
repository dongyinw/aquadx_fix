package icu.samnyan.aqua.net.games.mai2.circle

import ext.*
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Entity
@Table(name = "mai2_circle")
class Mai2CircleEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "circle_name", nullable = false, length = 32)
    var circleName: String = "",
    @Column(name = "circle_code", nullable = false, unique = true, length = 16)
    var circleCode: String = "",
    @Column(name = "owner_ext_id", nullable = false, unique = true)
    var ownerExtId: Long = 0,
    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean = true,
    @Column(name = "allow_anyone_join", nullable = false)
    var isAllowAnyoneJoin: Boolean = false,
    @Column(name = "circle_comment", nullable = false, length = 500)
    var comment: String = "",
    @Column(name = "circle_class", nullable = false)
    var circleClass: Int = 0,
    @Column(name = "is_place", nullable = false)
    var isPlace: Boolean = false,
    @Column(name = "place_id", nullable = false)
    var placeId: Int = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)

@Entity
@Table(name = "mai2_circle_member")
class Mai2CircleMemberEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "circle_id", nullable = false)
    var circleId: Long = 0,
    @Column(name = "aime_ext_id", nullable = false, unique = true)
    var aimeExtId: Long = 0,
    @Column(name = "joined_at", nullable = false)
    var joinedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "last_login_date", nullable = false, length = 19)
    var lastLoginDate: String = "",
    @Column(nullable = false)
    var point: Int = 0,
)

@Entity
@Table(name = "mai2_circle_join_request")
class Mai2CircleJoinRequestEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "circle_id", nullable = false)
    var circleId: Long = 0,
    @Column(name = "aime_ext_id", nullable = false, unique = true)
    var aimeExtId: Long = 0,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime = LocalDateTime.now(),
)

interface Mai2CircleRepo : JpaRepository<Mai2CircleEntity, Long> {
    fun findByCircleCodeIgnoreCase(circleCode: String): Mai2CircleEntity?
    @Query("select c from Mai2CircleEntity c where c.isPublic = true")
    fun findPublic(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Mai2CircleEntity>
    @Query("select c from Mai2CircleEntity c where c.isPlace = true and c.placeId = :placeId")
    fun findPlace(@Param("placeId") placeId: Int): Mai2CircleEntity?
}

interface Mai2CircleMemberRepo : JpaRepository<Mai2CircleMemberEntity, Long> {
    fun findByAimeExtId(aimeExtId: Long): Mai2CircleMemberEntity?
    fun findByCircleId(circleId: Long, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Mai2CircleMemberEntity>
    fun findByCircleIdOrderByPointDescJoinedAtAsc(circleId: Long): List<Mai2CircleMemberEntity>
    fun countByCircleId(circleId: Long): Long
    @Transactional fun deleteByCircleIdAndAimeExtId(circleId: Long, aimeExtId: Long)
}

interface Mai2CircleJoinRequestRepo : JpaRepository<Mai2CircleJoinRequestEntity, Long> {
    fun findByAimeExtId(aimeExtId: Long): Mai2CircleJoinRequestEntity?
    fun findByCircleId(circleId: Long, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Mai2CircleJoinRequestEntity>
    fun countByCircleId(circleId: Long): Long
    @Transactional fun deleteByCircleIdAndAimeExtId(circleId: Long, aimeExtId: Long)
}

data class CircleInput(
    val circleId: Long = 0,
    val circleName: String = "",
    val circleCode: String = "",
    val circleClass: Int = 0,
    val isPlace: Boolean = false,
    val placeId: Int = 0,
    val aggrDate: String = "",
    val isPublic: Boolean = true,
    val isAllowAnyoneJoin: Boolean = false,
    val comment: String = "",
)

data class CirclePage<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
)

@Service
class Mai2CircleService(
    private val us: AquaUserServices,
    private val mai2: Mai2Repos,
    private val circles: Mai2CircleRepo,
    private val members: Mai2CircleMemberRepo,
    private val requests: Mai2CircleJoinRequestRepo,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun nowString() = LocalDateTime.now().format(dateFormat)

    private suspend fun <T> withPlayer(token: String, aimeId: String, action: (Card, Long, String) -> T): T =
        us.jwt.auth(token) { account ->
            val card = us.cardRepo.findByLuid(aimeId)
                ?: aimeId.toLongOrNull()?.let(us.cardRepo::findByExtId)
                ?: (404 - "Card not found")
            if (card.aquaUser?.auId != account.auId) (403 - "Card is not linked to this account")
            val profile = mai2.userData.findByCardExtId(card.extId)
                ?: (404 - "Maimai DX profile not found on this card")
            action(card, profile.id, profile.userName)
        }

    private fun pageIndex(page: Int?) = (page ?: 0).coerceIn(0, 100_000)

    private fun circleView(circle: Mai2CircleEntity) = mapOf(
        "circleId" to circle.id,
        "circleClass" to circle.circleClass,
        "circleName" to circle.circleName,
        "isPlace" to circle.isPlace,
        "placeId" to circle.placeId,
        "isPublic" to circle.isPublic,
        "aggrDate" to "",
        "circleCode" to circle.circleCode,
        "comment" to circle.comment,
        "isAllowAnyoneJoin" to circle.isAllowAnyoneJoin,
        "memberCount" to members.countByCircleId(circle.id),
    )

    private fun memberView(member: Mai2CircleMemberEntity, ownerExtId: Long = 0): Map<String, Any?> {
        val user = mai2.userData.findByCardExtId(member.aimeExtId)
        return mapOf(
            "userCode" to member.aimeExtId.toString(),
            "isOwner" to (member.aimeExtId == ownerExtId),
            "userProfile" to mapOf(
                "userName" to (user?.userName ?: "Unknown player"),
                "playerRating" to (user?.playerRating ?: 0),
                "lastPlayDate" to (user?.lastPlayDate ?: ""),
            ),
            "userCircleData" to mapOf(
                "id" to member.id,
                "circleId" to member.circleId,
                "lastLoginDate" to member.lastLoginDate,
            ),
            "userCirclePointData" to mapOf(
                "id" to member.id,
                "circleId" to member.circleId,
                "userName" to (user?.userName ?: "Unknown player"),
                "aggrDate" to "",
                "point" to member.point,
                "recordDate" to member.lastLoginDate,
                "rewardGet" to false,
            ),
            "userCircleChallenge" to null,
        )
    }

    private fun requireOwner(circle: Mai2CircleEntity, extId: Long) {
        if (circle.ownerExtId != extId) (403 - "Circle owner permission required")
    }

    private fun requireNoCircle(extId: Long) {
        if (members.findByAimeExtId(extId) != null) (409 - "This card already belongs to a circle")
    }

    @Transactional
    suspend fun userCircleInfo(token: String, aimeId: String): Map<String, Any?> = withPlayer(token, aimeId) { card, _, userName ->
        val member = members.findByAimeExtId(card.extId)
        val circle = member?.let { circles.findById(it.circleId).orElse(null) }
        val ordered = circle?.let { members.findByCircleIdOrderByPointDescJoinedAtAsc(it.id) }.orEmpty()
        val rank = if ((member?.point ?: 0) > 0) ordered.indexOfFirst { it.aimeExtId == card.extId }.let { if (it < 0) 0 else it + 1 } else 0
        mapOf(
            "joinedCircle" to circle?.let(::circleView),
            "circleChallenge" to null,
            "userCircleData" to member?.let { mapOf("id" to it.id, "circleId" to it.circleId, "lastLoginDate" to it.lastLoginDate) },
            "userCirclePointData" to member?.let { mapOf("id" to it.id, "circleId" to it.circleId, "userName" to userName, "aggrDate" to "", "point" to it.point, "recordDate" to it.lastLoginDate, "rewardGet" to false) },
            "userCirclePointRankingResult" to circle?.let { mapOf("id" to it.id, "circleId" to it.id, "aggrDate" to "", "circleName" to it.circleName, "lastMonthCircleRank" to rank, "lastMonthPoint" to member.point) },
            "userCircleChallenge" to null,
            "isCircleOwner" to (circle?.ownerExtId == card.extId),
        )
    }

    @Transactional(readOnly = true)
    suspend fun publicCircles(token: String, aimeId: String, page: Int?): CirclePage<Map<String, Any?>> = withPlayer(token, aimeId) { _, _, _ ->
        val p = pageIndex(page)
        val result = circles.findPublic(PageRequest.of(p, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
        CirclePage(result.content.map(::circleView), result.totalElements, result.totalPages, p)
    }

    @Transactional(readOnly = true)
    suspend fun circleMembers(token: String, aimeId: String, page: Int?): CirclePage<Map<String, Any?>> = withPlayer(token, aimeId) { card, _, _ ->
        val member = members.findByAimeExtId(card.extId) ?: (404 - "Circle not found")
        val circle = circles.findById(member.circleId).orElse(null) ?: (404 - "Circle not found")
        requireOwner(circle, card.extId)
        val p = pageIndex(page)
        val result = members.findByCircleId(circle.id, PageRequest.of(p, 10, Sort.by(Sort.Direction.ASC, "joinedAt")))
        CirclePage(result.content.map { memberView(it, circle.ownerExtId) }, result.totalElements, result.totalPages, p)
    }

    @Transactional(readOnly = true)
    suspend fun joinRequests(token: String, aimeId: String, page: Int?): CirclePage<Map<String, Any?>> = withPlayer(token, aimeId) { card, _, _ ->
        val member = members.findByAimeExtId(card.extId) ?: (404 - "Circle not found")
        val circle = circles.findById(member.circleId).orElse(null) ?: (404 - "Circle not found")
        requireOwner(circle, card.extId)
        val p = pageIndex(page)
        val result = requests.findByCircleId(circle.id, PageRequest.of(p, 10, Sort.by(Sort.Direction.ASC, "requestedAt")))
        CirclePage(result.content.map { request ->
            val user = mai2.userData.findByCardExtId(request.aimeExtId)
            mapOf(
                "userCode" to request.aimeExtId.toString(),
                "requestTime" to request.requestedAt.format(dateFormat),
                "userProfile" to mapOf(
                    "userName" to (user?.userName ?: "Unknown player"),
                    "playerRating" to (user?.playerRating ?: 0),
                    "lastPlayDate" to (user?.lastPlayDate ?: ""),
                ),
            )
        }, result.totalElements, result.totalPages, p)
    }

    @Transactional
    suspend fun createCircle(token: String, aimeId: String, input: CircleInput): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        requireNoCircle(card.extId)
        if (input.circleName.isBlank() || input.circleName.trim().length > 32) (400 - "Circle name must be 1-32 characters")
        val circle = circles.saveAndFlush(Mai2CircleEntity(
            circleName = input.circleName.trim(), circleCode = newCode(), ownerExtId = card.extId,
            isPublic = input.isPublic, isAllowAnyoneJoin = input.isAllowAnyoneJoin,
            comment = input.comment.trim().take(500), createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
        ))
        members.save(Mai2CircleMemberEntity(circleId = circle.id, aimeExtId = card.extId, lastLoginDate = nowString()))
        true
    }

    @Transactional
    suspend fun updateCircle(token: String, aimeId: String, input: CircleInput): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val circle = ownerCircle(card.extId)
        if (input.circleName.isBlank() || input.circleName.trim().length > 32) (400 - "Circle name must be 1-32 characters")
        circle.circleName = input.circleName.trim()
        circle.comment = input.comment.trim().take(500)
        circle.isPublic = input.isPublic
        circle.isAllowAnyoneJoin = input.isAllowAnyoneJoin
        circle.updatedAt = LocalDateTime.now()
        circles.save(circle)
        true
    }

    @Transactional
    suspend fun requestJoin(token: String, aimeId: String, circleId: Long): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        requireNoCircle(card.extId)
        val circle = circles.findById(circleId).orElse(null) ?: (404 - "Circle not found")
        if (!circle.isPublic) (404 - "Circle not found")
        if (circle.isAllowAnyoneJoin) addMember(circle.id, card.extId)
        else {
            val existing = requests.findByAimeExtId(card.extId)
            if (existing != null) {
                if (existing.circleId == circle.id) (409 - "Join request already exists")
                requests.delete(existing)
            }
            requests.save(Mai2CircleJoinRequestEntity(circleId = circle.id, aimeExtId = card.extId))
        }
        true
    }

    @Transactional
    suspend fun requestJoinByCode(token: String, aimeId: String, circleCode: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val circle = circles.findByCircleCodeIgnoreCase(circleCode.trim()) ?: (404 - "Circle not found")
        requireNoCircle(card.extId)
        if (circle.isAllowAnyoneJoin) addMember(circle.id, card.extId)
        else {
            val existing = requests.findByAimeExtId(card.extId)
            if (existing?.circleId == circle.id) (409 - "Join request already exists")
            if (existing != null) requests.delete(existing)
            requests.save(Mai2CircleJoinRequestEntity(circleId = circle.id, aimeExtId = card.extId))
        }
        true
    }

    @Transactional
    suspend fun approve(token: String, aimeId: String, userCode: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val circle = ownerCircle(card.extId)
        val extId = userCode.toLongOrNull() ?: (400 - "Invalid player code")
        val request = requests.findByAimeExtId(extId) ?: (404 - "Join request not found")
        if (request.circleId != circle.id) (404 - "Join request not found")
        requireNoCircle(extId)
        addMember(circle.id, extId)
        requests.delete(request)
        true
    }

    @Transactional
    suspend fun reject(token: String, aimeId: String, userCode: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val circle = ownerCircle(card.extId)
        val extId = userCode.toLongOrNull() ?: (400 - "Invalid player code")
        requests.deleteByCircleIdAndAimeExtId(circle.id, extId)
        true
    }

    @Transactional
    suspend fun kick(token: String, aimeId: String, userCode: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val circle = ownerCircle(card.extId)
        val extId = userCode.toLongOrNull() ?: (400 - "Invalid player code")
        if (extId == circle.ownerExtId) (400 - "The circle owner cannot be removed")
        members.deleteByCircleIdAndAimeExtId(circle.id, extId)
        true
    }

    @Transactional
    suspend fun exitCircle(token: String, aimeId: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        val member = members.findByAimeExtId(card.extId) ?: (404 - "Circle not found")
        val circle = circles.findById(member.circleId).orElse(null) ?: (404 - "Circle not found")
        if (circle.ownerExtId == card.extId) (400 - "The owner must dissolve the circle")
        members.delete(member)
        true
    }

    @Transactional
    suspend fun dissolveCircle(token: String, aimeId: String): Boolean = withPlayer(token, aimeId) { card, _, _ ->
        circles.delete(ownerCircle(card.extId))
        true
    }

    private fun ownerCircle(extId: Long): Mai2CircleEntity {
        val member = members.findByAimeExtId(extId) ?: (404 - "Circle not found")
        val circle = circles.findById(member.circleId).orElse(null) ?: (404 - "Circle not found")
        requireOwner(circle, extId)
        return circle
    }

    private fun addMember(circleId: Long, extId: Long) {
        requests.findByAimeExtId(extId)?.let(requests::delete)
        members.save(Mai2CircleMemberEntity(circleId = circleId, aimeExtId = extId, lastLoginDate = nowString()))
    }

    private fun newCode(): String {
        repeat(5) {
            val code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            if (circles.findByCircleCodeIgnoreCase(code) == null) return code
        }
        throw IllegalStateException("Unable to generate a circle code")
    }

    @Transactional
    fun gameUserCircleData(extId: Long): Map<String, Any?> {
        val member = members.findByAimeExtId(extId)
            ?: return mapOf("circleId" to 0, "circleName" to "一緒に歌おう！", "isPlace" to false, "circleClass" to 0, "lastLoginDate" to "", "circlePointRankingList" to emptyList<Any>())
        val circle = circles.findById(member.circleId).orElse(null)
            ?: return mapOf("circleId" to 0, "circleName" to "一緒に歌おう！", "isPlace" to false, "circleClass" to 0, "lastLoginDate" to "", "circlePointRankingList" to emptyList<Any>())
        member.lastLoginDate = nowString()
        members.save(member)
        return mapOf("circleId" to circle.id, "circleName" to circle.circleName, "isPlace" to circle.isPlace, "circleClass" to circle.circleClass, "lastLoginDate" to member.lastLoginDate, "circlePointRankingList" to emptyList<Any>())
    }

    @Transactional(readOnly = true)
    fun gamePlaceCircleData(placeId: Int): Map<String, Any> {
        val circle = circles.findPlace(placeId)
        return mapOf("returnCode" to 0, "circleId" to (circle?.id ?: 0L), "aggrDate" to "")
    }

    @Transactional(readOnly = true)
    fun gameUserCircleChallenge(extId: Long): Map<String, Any?> = mapOf(
        "userId" to extId,
        "userCircleChallenge" to null,
        "circleCircleChallenge" to null,
        "achievement" to 0,
    )

    @Transactional(readOnly = true)
    fun gameUserCirclePointData(extId: Long): Map<String, Any?> {
        val member = members.findByAimeExtId(extId)
        val circle = member?.let { circles.findById(it.circleId).orElse(null) }
        val user = mai2.userData.findByCardExtId(extId)
        val list = if (member == null || circle == null) emptyList() else listOf(
            mapOf("id" to member.id, "circleId" to circle.id, "userName" to (user?.userName ?: ""), "aggrDate" to "", "point" to member.point, "recordDate" to member.lastLoginDate, "rewardGet" to false)
        )
        return mapOf("userId" to extId, "aggrDate" to "", "userCirclePointDataList" to list)
    }

    @Transactional(readOnly = true)
    fun gameUserCirclePointRanking(extId: Long): Map<String, Any?> {
        val member = members.findByAimeExtId(extId)
        val circle = member?.let { circles.findById(it.circleId).orElse(null) }
            ?: return mapOf("circleId" to 0, "circleName" to "一緒に歌おう！", "aggrDate" to "", "lastMonthCircleRank" to 0, "lastMonthPoint" to 0)
        val ranked = members.findByCircleIdOrderByPointDescJoinedAtAsc(circle.id)
        val rank = if (member.point > 0) ranked.indexOfFirst { it.aimeExtId == extId }.let { if (it < 0) 0 else it + 1 } else 0
        return mapOf("circleId" to circle.id, "circleName" to circle.circleName, "aggrDate" to "", "lastMonthCircleRank" to rank, "lastMonthPoint" to member.point)
    }
}

@RestController
@API("api/game/maimai2")
class Mai2CircleApi(private val service: Mai2CircleService) {
    @API("circle")
    suspend fun circle(@RP token: String, @RP aimeId: String, @RP(required = false) page: Int?) = service.publicCircles(token, aimeId, page)

    @API("circleMemberUser")
    suspend fun circleMemberUser(@RP token: String, @RP aimeId: String, @RP(required = false) page: Int?) = service.circleMembers(token, aimeId, page)

    @API("requestJoinCircleList")
    suspend fun requestJoinCircleList(@RP token: String, @RP aimeId: String, @RP(required = false) page: Int?) = service.joinRequests(token, aimeId, page)

    @API("userCircleInfo")
    suspend fun userCircleInfo(@RP token: String, @RP aimeId: String) = service.userCircleInfo(token, aimeId)

    @API("createCircle")
    suspend fun createCircle(@RP token: String, @RP aimeId: String, @RB circle: CircleInput) = service.createCircle(token, aimeId, circle)

    @API("updateCircle")
    suspend fun updateCircle(@RP token: String, @RP aimeId: String, @RB circle: CircleInput) = service.updateCircle(token, aimeId, circle)

    @API("requestJoinCircle")
    suspend fun requestJoinCircle(@RP token: String, @RP aimeId: String, @RP circleId: Long) = service.requestJoin(token, aimeId, circleId)

    @API("requestJoinCircleByCode")
    suspend fun requestJoinCircleByCode(@RP token: String, @RP aimeId: String, @RP circleCode: String) = service.requestJoinByCode(token, aimeId, circleCode)

    @API("deleteUserToCircle")
    suspend fun deleteUserToCircle(@RP token: String, @RP aimeId: String, @RP userCode: String) = service.kick(token, aimeId, userCode)

    @API("exitCircle")
    suspend fun exitCircle(@RP token: String, @RP aimeId: String) = service.exitCircle(token, aimeId)

    @API("dissolveCircle")
    suspend fun dissolveCircle(@RP token: String, @RP aimeId: String) = service.dissolveCircle(token, aimeId)

    @API("approveUserJoinCircle")
    suspend fun approveUserJoinCircle(@RP token: String, @RP aimeId: String, @RP userCode: String) = service.approve(token, aimeId, userCode)

    @API("rejectUserJoinCircle")
    suspend fun rejectUserJoinCircle(@RP token: String, @RP aimeId: String, @RP userCode: String) = service.reject(token, aimeId, userCode)
}
