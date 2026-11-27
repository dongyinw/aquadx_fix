package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaNetUserRepo
import icu.samnyan.aqua.net.games.GameApiController
import icu.samnyan.aqua.net.games.IUserData
import icu.samnyan.aqua.net.utils.ApiException
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController

/** A player's request to change an in-game name while [Feature.NAME_REVIEW] is on. */
@Entity
@Table(name = "game_name_request")
class GameNameRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var auId: Long = 0,
    var game: String = "",
    var oldName: String = "",
    var newName: String = "",
    /** PENDING, APPROVED or REJECTED. */
    var status: String = NameReviewService.PENDING,
    var submittedAt: Long = 0,
    var reviewedAt: Long? = null,
    var reviewerAuId: Long? = null,
    /** Approved by the timer instead of an admin. Only admins ever see this. */
    var autoApproved: Boolean = false,
    var rejectReason: String = "",
    /** The player has been shown the outcome (home page popup). */
    var notified: Boolean = false,
)

interface GameNameRequestRepo : JpaRepository<GameNameRequest, Long> {
    fun findByAuIdAndGameAndStatus(auId: Long, game: String, status: String): List<GameNameRequest>
    fun findByAuIdOrderBySubmittedAtDesc(auId: Long): List<GameNameRequest>
    fun findByAuIdAndNotifiedFalseAndStatusNotOrderByReviewedAtAsc(auId: Long, status: String): List<GameNameRequest>
    fun findByStatusOrderBySubmittedAtAsc(status: String): List<GameNameRequest>
    fun findByStatusAndSubmittedAtLessThanEqual(status: String, submittedAt: Long): List<GameNameRequest>
    fun findTop200ByOrderBySubmittedAtDesc(): List<GameNameRequest>
}

@Service
class NameReviewService(
    private val requests: GameNameRequestRepo,
    private val users: AquaNetUserRepo,
    private val features: FeatureSwitches,
    private val games: ObjectProvider<GameApiController<*>>,
) {
    companion object {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val REJECTED = "REJECTED"

        /** Requests nobody has answered by then are approved. Deliberately not mentioned to players. */
        const val AUTO_APPROVE_MS = 48L * 3600 * 1000
        private val log = LoggerFactory.getLogger(NameReviewService::class.java)
    }

    val enabled get() = features.enabled(Feature.NAME_REVIEW)

    @Suppress("UNCHECKED_CAST")
    private fun game(name: Str) = games.orderedStream().toList().find { it.name == name } as GameApiController<IUserData>?
        ?: (400 - "Unsupported game")

    /**
     * Records [newName] (already validated) as pending for [user]. A player has at most one open request per game;
     * submitting again replaces it.
     */
    @Synchronized
    @Transactional
    fun submit(user: AquaNetUser, game: Str, currentName: Str, newName: Str): GameNameRequest {
        if (newName == currentName) 400 - "新名字与当前名字相同"
        val open = requests.findByAuIdAndGameAndStatus(user.auId, game, PENDING)
        val req = open.firstOrNull() ?: GameNameRequest(auId = user.auId, game = game)
        if (open.size > 1) requests.deleteAll(open.drop(1))
        req.oldName = currentName
        req.newName = newName
        req.submittedAt = System.currentTimeMillis()
        return requests.save(req)
    }

    /** Writes the requested name to the player's profile. Returns false when the profile no longer exists. */
    private fun apply(req: GameNameRequest): Boolean {
        val user = users.findByAuId(req.auId) ?: return false
        val ctrl = game(req.game)
        val data = ctrl.userDataRepo.findByCard(user.ghostCard) ?: return false
        data.userName = req.newName
        ctrl.userDataRepo.save(data)
        ctrl.cardService.updateCardTimestamp(user.ghostCard, req.game)
        return true
    }

    private fun finish(req: GameNameRequest, status: Str, reviewer: Long?, auto: Bool = false, reason: Str = "") {
        req.status = status
        req.reviewedAt = System.currentTimeMillis()
        req.reviewerAuId = reviewer
        req.autoApproved = auto
        req.rejectReason = reason
        requests.save(req)
    }

    // The "profile is gone" outcome is stored even though the call ends in an error.
    @Synchronized
    @Transactional(noRollbackFor = [ApiException::class])
    fun approve(id: Long, reviewer: Long?, auto: Bool = false): GameNameRequest {
        val req = requests.findById(id).orElse(null) ?: (404 - "申请不存在")
        if (req.status != PENDING) 400 - "该申请已被处理"
        if (!apply(req)) {
            finish(req, REJECTED, reviewer, reason = "玩家的游戏档案已不存在")
            400 - "玩家的游戏档案已不存在，申请已关闭"
        }
        finish(req, APPROVED, reviewer, auto)
        log.info("Name request {} ({} -> {}) approved by {}", req.id, req.oldName, req.newName, reviewer ?: "timer")
        return req
    }

    @Synchronized
    @Transactional
    fun reject(id: Long, reviewer: Long, reason: Str): GameNameRequest {
        val req = requests.findById(id).orElse(null) ?: (404 - "申请不存在")
        if (req.status != PENDING) 400 - "该申请已被处理"
        finish(req, REJECTED, reviewer, reason = reason.trim().take(255))
        log.info("Name request {} ({} -> {}) rejected by {}", req.id, req.oldName, req.newName, reviewer)
        return req
    }

    /** Approves everything that has waited long enough. Runs even if the switch was turned off meanwhile. */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    fun autoApprove(now: Long = System.currentTimeMillis()) {
        requests.findByStatusAndSubmittedAtLessThanEqual(PENDING, now - AUTO_APPROVE_MS).forEach {
            try { approve(it.id, null, auto = true) } catch (e: Exception) { log.warn("Auto approving name request {} failed: {}", it.id, e.message) }
        }
    }

    /** Reviewed requests whose outcome the player has not been shown yet. */
    fun unseen(user: AquaNetUser) = requests.findByAuIdAndNotifiedFalseAndStatusNotOrderByReviewedAtAsc(user.auId, PENDING)

    /** Marks the given outcomes (only the player's own) as shown. */
    @Transactional
    fun acknowledge(user: AquaNetUser, ids: Collection<Long>) {
        unseen(user).filter { it.id in ids }.forEach { it.notified = true; requests.save(it) }
    }

    fun mine(user: AquaNetUser) = requests.findByAuIdOrderBySubmittedAtDesc(user.auId)
    fun pending() = requests.findByStatusOrderBySubmittedAtAsc(PENDING)
    fun recent() = requests.findTop200ByOrderBySubmittedAtDesc()
}

/** What a player may see about their own requests. No reviewer, no automatic-approval flag, no deadline. */
@RestController
@API("/api/v2/name-review")
class NameReviewApi(private val jwt: JWT, private val review: NameReviewService) {
    @API("/status")
    @Doc("Whether name changes need review, and the player's latest request per game.", "Name review")
    fun status(@RP token: Str) = jwt.auth(token) { u ->
        mapOf(
            "enabled" to review.enabled,
            "requests" to review.mine(u).distinctBy { it.game }.map {
                mapOf("game" to it.game, "newName" to it.newName, "status" to it.status, "rejectReason" to it.rejectReason, "submittedAt" to it.submittedAt)
            },
        )
    }

    @API("/notices")
    @Doc("Outcomes of reviewed name requests the player has not been shown yet.", "Name review")
    fun notices(@RP token: Str) = jwt.auth(token) { u ->
        review.unseen(u).map {
            mapOf("id" to it.id, "game" to it.game, "newName" to it.newName, "approved" to (it.status == NameReviewService.APPROVED),
                "rejectReason" to it.rejectReason, "submittedAt" to it.submittedAt)
        }
    }

    @API("/ack")
    @Doc("Mark outcomes as shown.", "Success message")
    fun ack(@RP token: Str, @RP ids: Str) = jwt.auth(token) { u ->
        review.acknowledge(u, ids.split(',').mapNotNull { it.trim().toLongOrNull() })
        mapOf("success" to true)
    }
}

@RestController
@API("/api/v2/admin/name-review")
class NameReviewAdminApi(
    private val jwt: JWT,
    private val review: NameReviewService,
    private val users: AquaNetUserRepo,
) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    private fun payload(list: List<GameNameRequest>): List<Map<String, Any?>> {
        val byId = users.findAllById((list.map { it.auId } + list.mapNotNull { it.reviewerAuId }).toSet()).associateBy { it.auId }
        return list.map {
            mapOf(
                "id" to it.id,
                "auId" to it.auId,
                "username" to byId[it.auId]?.username,
                "game" to it.game,
                "oldName" to it.oldName,
                "newName" to it.newName,
                "status" to it.status,
                "submittedAt" to it.submittedAt,
                "reviewedAt" to it.reviewedAt,
                "reviewer" to it.reviewerAuId?.let { id -> byId[id]?.username },
                "autoApproved" to it.autoApproved,
                "rejectReason" to it.rejectReason,
            )
        }
    }

    @API("/list")
    @Doc("Pending name requests (oldest first), or with all=true the 200 latest requests of any status.", "Name review")
    fun list(@RP token: Str, @RP all: Boolean?): Map<String, Any?> {
        admin(token)
        return mapOf(
            "enabled" to review.enabled,
            "requests" to payload(if (all == true) review.recent() else review.pending()),
        )
    }

    @API("/approve")
    @Doc("Approve a name request and apply the name.", "Name review")
    fun approve(@RP token: Str, @RP id: Long): Map<String, Any?> {
        val actor = admin(token)
        return payload(listOf(review.approve(id, actor.auId))).first()
    }

    @API("/reject")
    @Doc("Reject a name request.", "Name review")
    fun reject(@RP token: Str, @RP id: Long, @RP reason: Str?): Map<String, Any?> {
        val actor = admin(token)
        return payload(listOf(review.reject(id, actor.auId, reason ?: ""))).first()
    }
}
