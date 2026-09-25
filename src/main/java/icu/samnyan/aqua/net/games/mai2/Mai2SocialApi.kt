package icu.samnyan.aqua.net.games.mai2

import ext.*
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.UserRivalMusic
import icu.samnyan.aqua.sega.maimai2.model.UserRivalMusicDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGeneralData
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Entity
@Table(
    name = "mai2_social_friend",
    uniqueConstraints = [UniqueConstraint(name = "uk_mai2_social_pair", columnNames = ["user_low_id", "user_high_id"])],
)
class Mai2SocialFriend(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "user_low_id", nullable = false)
    var userLowId: Long = 0,
    @Column(name = "user_high_id", nullable = false)
    var userHighId: Long = 0,
    @Column(name = "friend_status", nullable = false, length = 16)
    var status: String = PENDING,
    @Column(name = "requested_by_user_id", nullable = false)
    var requestedByUserId: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val PENDING = "PENDING"
        const val ACCEPTED = "ACCEPTED"
    }
}

interface Mai2SocialFriendRepo : JpaRepository<Mai2SocialFriend, Long> {
    fun findByUserLowIdAndUserHighId(userLowId: Long, userHighId: Long): Mai2SocialFriend?

    @Query("select f from Mai2SocialFriend f where f.userLowId = :userId or f.userHighId = :userId order by f.updatedAt desc")
    fun findForUser(@Param("userId") userId: Long): List<Mai2SocialFriend>
}

@Service
class Mai2SocialService(
    private val us: AquaUserServices,
    private val mai2: Mai2Repos,
    private val friendships: Mai2SocialFriendRepo,
) {
    private data class PlayerTarget(val profile: Mai2UserDetail, val username: String)

    private fun pair(a: Long, b: Long) = minOf(a, b) to maxOf(a, b)

    private fun relation(a: Long, b: Long): Mai2SocialFriend? {
        val (low, high) = pair(a, b)
        return friendships.findByUserLowIdAndUserHighId(low, high)
    }

    private suspend fun <T> withPlayer(
        token: String,
        aimeId: String,
        action: suspend (Card, Mai2UserDetail) -> T,
    ): T = us.jwt.auth(token) { account ->
        val card = us.cardRepo.findByLuid(aimeId)
            ?: aimeId.toLongOrNull()?.let(us.cardRepo::findByExtId)
            ?: (404 - "Card not found")
        if (card.aquaUser?.auId != account.auId) (403 - "Card is not linked to this account")
        val profile = mai2.userData.findByCardExtId(card.extId) ?: (404 - "Maimai DX profile not found on this card")
        action(card, profile)
    }

    private fun playerView(profile: Mai2UserDetail, username: String = "") = mapOf(
        "id" to profile.id,
        "username" to username,
        "playerName" to profile.userName,
        "rating" to profile.playerRating,
        "lastPlayDate" to profile.lastPlayDate,
    )

    private suspend fun targetProfile(identifier: String): PlayerTarget {
        val value = identifier.trim()
        val code = value.uppercase().removePrefix("MN-").replace("-", "")
        val (card, username) = if (code.matches(Regex("[0-9A-F]{16}"))) {
            val user = us.userRepo.findByFriendCode(code) ?: (404 - "Friend code not found")
            us.cardByName(user.username) to user.username
        } else {
            us.cardByName(value) to value
        }
        val profile = mai2.userData.findByCardExtId(card.extId)
            ?: (404 - "Maimai profile not found")
        return PlayerTarget(profile, username)
    }

    private fun rivalIds(profile: Mai2UserDetail): List<Long> =
        mai2.userGeneralData.findByUserAndPropertyKey(profile, "favorite_rival")
            ?.propertyValue?.split(',')?.mapNotNull { it.toLongOrNull() }?.distinct() ?: emptyList()

    private fun isRival(profile: Mai2UserDetail, targetId: Long) = targetId in rivalIds(profile)

    private fun relationView(profile: Mai2UserDetail, row: Mai2SocialFriend): Map<String, Any?> {
        val otherId = if (row.userLowId == profile.id) row.userHighId else row.userLowId
        val other = mai2.userData.findById(otherId).orElse(null)
        return (other?.let(::playerView) ?: mapOf("id" to otherId, "username" to "", "playerName" to "Unknown player", "rating" to 0)) + mapOf(
            "status" to row.status,
            "direction" to when {
                row.status == Mai2SocialFriend.ACCEPTED -> "friend"
                row.requestedByUserId == profile.id -> "outgoing"
                else -> "incoming"
            },
            "isRival" to isRival(profile, otherId),
            "requestedAt" to row.createdAt.toString(),
        )
    }

    @Transactional(readOnly = true)
    suspend fun state(token: String, aimeId: String) = withPlayer(token, aimeId) { card, profile ->
        val rows = friendships.findForUser(profile.id)
        val rivals = rivalIds(profile).mapNotNull { mai2.userData.findById(it).orElse(null) }.map(::playerView)
        mapOf(
            "friendCode" to card.aquaUser?.friendCode,
            "friends" to rows.filter { it.status == Mai2SocialFriend.ACCEPTED }.map { relationView(profile, it) },
            "incomingRequests" to rows.filter { it.status == Mai2SocialFriend.PENDING && it.requestedByUserId != profile.id }.map { relationView(profile, it) },
            "outgoingRequests" to rows.filter { it.status == Mai2SocialFriend.PENDING && it.requestedByUserId == profile.id }.map { relationView(profile, it) },
            "rivals" to rivals,
        )
    }

    @Transactional(readOnly = true)
    suspend fun search(token: String, aimeId: String, username: String): Map<String, Any?> = withPlayer(token, aimeId) { _, profile ->
        val target = targetProfile(username)
        if (target.profile.id == profile.id) (400 - "You cannot add yourself")
        val row = relation(profile.id, target.profile.id)
        playerView(target.profile, target.username) + mapOf(
            "status" to row?.status,
            "direction" to row?.let {
                if (it.status == Mai2SocialFriend.ACCEPTED) "friend"
                else if (it.requestedByUserId == profile.id) "outgoing" else "incoming"
            },
            "isRival" to isRival(profile, target.profile.id),
        )
    }

    @Transactional
    suspend fun requestFriend(token: String, aimeId: String, username: String) = withPlayer(token, aimeId) { _, profile ->
        val target = targetProfile(username)
        if (target.profile.id == profile.id) (400 - "You cannot add yourself")
        val now = LocalDateTime.now()
        val row = relation(profile.id, target.profile.id)
        when {
            row?.status == Mai2SocialFriend.ACCEPTED -> mapOf("status" to row.status)
            row?.status == Mai2SocialFriend.PENDING && row.requestedByUserId != profile.id -> {
                row.status = Mai2SocialFriend.ACCEPTED
                row.updatedAt = now
                friendships.save(row)
                mapOf("status" to row.status)
            }
            row?.status == Mai2SocialFriend.PENDING -> mapOf("status" to row.status)
            else -> {
                val (low, high) = pair(profile.id, target.profile.id)
                friendships.save(Mai2SocialFriend(
                    userLowId = low,
                    userHighId = high,
                    status = Mai2SocialFriend.PENDING,
                    requestedByUserId = profile.id,
                    createdAt = now,
                    updatedAt = now,
                ))
                mapOf("status" to Mai2SocialFriend.PENDING)
            }
        }
    }

    @Transactional
    suspend fun acceptFriend(token: String, aimeId: String, friendId: Long) = withPlayer(token, aimeId) { _, profile ->
        val row = relation(profile.id, friendId) ?: (404 - "Friend request not found")
        if (row.status != Mai2SocialFriend.PENDING || row.requestedByUserId == profile.id) (400 - "No incoming request")
        row.status = Mai2SocialFriend.ACCEPTED
        row.updatedAt = LocalDateTime.now()
        friendships.save(row)
        true
    }

    @Transactional
    suspend fun rejectFriend(token: String, aimeId: String, friendId: Long) = withPlayer(token, aimeId) { _, profile ->
        val row = relation(profile.id, friendId) ?: (404 - "Friend request not found")
        if (row.status != Mai2SocialFriend.PENDING || row.requestedByUserId == profile.id) (400 - "No incoming request")
        friendships.delete(row)
        true
    }

    @Transactional
    suspend fun removeFriend(token: String, aimeId: String, friendId: Long) = withPlayer(token, aimeId) { _, profile ->
        val row = relation(profile.id, friendId) ?: (404 - "Friendship not found")
        if (row.status == Mai2SocialFriend.PENDING && row.requestedByUserId != profile.id) (400 - "Use reject for an incoming request")
        friendships.delete(row)
        true
    }

    @Transactional
    suspend fun setRival(token: String, aimeId: String, rivalId: Long, add: Boolean) = withPlayer(token, aimeId) { _, profile ->
        if (rivalId == profile.id) (400 - "You cannot set yourself as a rival")
        val rival = mai2.userData.findById(rivalId).orElse(null) ?: (404 - "Maimai profile not found")
        val row = mai2.userGeneralData.findByUserAndPropertyKey(profile, "favorite_rival")
            ?: Mai2UserGeneralData().apply { user = profile; propertyKey = "favorite_rival" }
        val ids = row.propertyValue.split(',').mapNotNull { it.toLongOrNull() }.distinct().toMutableList()
        if (add && rivalId !in ids) {
            if (ids.size >= 4) (400 - "Rival list is full")
            ids.add(rival.id)
        } else if (!add) {
            ids.remove(rivalId)
        }
        row.propertyValue = ids.joinToString(",")
        mai2.userGeneralData.save(row)
        mapOf("rivals" to ids)
    }

    private fun gamePair(extId: Long, userId1: Long, userId2: Long): Pair<Mai2UserDetail, Mai2UserDetail>? {
        if (userId1 == userId2) return null
        val owner = mai2.userData.findByCardExtId(extId) ?: return null
        if (owner.id != userId1 && owner.id != userId2) return null
        val first = mai2.userData.findById(userId1).orElse(null) ?: return null
        val second = mai2.userData.findById(userId2).orElse(null) ?: return null
        return first to second
    }

    @Transactional(readOnly = true)
    fun gameFriendCheck(extId: Long, userId1: Long, userId2: Long): Map<String, Int> {
        val pair = gamePair(extId, userId1, userId2) ?: return mapOf("returnCode" to 0)
        val existing = relation(pair.first.id, pair.second.id)?.status == Mai2SocialFriend.ACCEPTED
        return mapOf("returnCode" to if (existing) 0 else 1)
    }

    @Transactional
    fun gameFriendRegist(extId: Long, userId1: Long, userId2: Long): Map<String, Int> {
        val players = gamePair(extId, userId1, userId2) ?: return mapOf("returnCode1" to 0, "returnCode2" to 0)
        val (first, second) = players
        val row = relation(first.id, second.id)
        if (row?.status == Mai2SocialFriend.ACCEPTED) return mapOf("returnCode1" to 1, "returnCode2" to 1)
        val now = LocalDateTime.now()
        if (row == null) {
            val (low, high) = pair(first.id, second.id)
            friendships.save(Mai2SocialFriend(
                userLowId = low,
                userHighId = high,
                status = Mai2SocialFriend.ACCEPTED,
                requestedByUserId = first.id,
                createdAt = now,
                updatedAt = now,
            ))
        } else {
            row.status = Mai2SocialFriend.ACCEPTED
            row.updatedAt = now
            friendships.save(row)
        }
        return mapOf("returnCode1" to 2, "returnCode2" to 2)
    }

    @Transactional(readOnly = true)
    fun gameTransferFriends(extId: Long): Map<String, Any?> {
        val profile = mai2.userData.findByCardExtId(extId)
            ?: return mapOf("userId" to extId, "transferFriendList" to emptyList<Any>())
        val favorites = rivalIds(profile).toSet()
        val friends = friendships.findForUser(profile.id)
            .filter { it.status == Mai2SocialFriend.ACCEPTED }
            .mapNotNull { row ->
                val friendId = if (row.userLowId == profile.id) row.userHighId else row.userLowId
                mai2.userData.findById(friendId).orElse(null)?.let { friend ->
                    mapOf(
                        "playUserId" to friend.id,
                        "playUserName" to friend.userName,
                        "playDate" to friend.lastPlayDate,
                        "friendPoint" to 0,
                        "isFavorite" to (friend.id in favorites),
                    )
                }
            }
        return mapOf("userId" to extId, "transferFriendList" to friends)
    }

    @Transactional(readOnly = true)
    fun gameRivalData(extId: Long, rivalId: Long): Map<String, Any?> {
        val owner = mai2.userData.findByCardExtId(extId) ?: (404 - "Maimai profile not found")
        if (!isRival(owner, rivalId)) (404 - "Rival not found")
        val rival = mai2.userData.findById(rivalId).orElse(null) ?: (404 - "Rival not found")
        return mapOf("userId" to extId, "userRivalData" to mapOf("rivalId" to rival.id, "rivalName" to rival.userName))
    }

    @Transactional(readOnly = true)
    fun gameRivalMusic(extId: Long, data: Map<String, Any?>): Map<String, Any?> {
        val rivalId = (data["rivalId"] as? Number)?.toLong() ?: (400 - "Invalid rivalId")
        val owner = mai2.userData.findByCardExtId(extId) ?: (404 - "Maimai profile not found")
        if (!isRival(owner, rivalId)) (404 - "Rival not found")
        val levels = (data["userRivalMusicLevelList"] as? List<*>)
            ?.mapNotNull { ((it as? Map<*, *>)?.get("level") as? Number)?.toInt() }
            ?.toSet() ?: setOf(0, 1, 2, 3, 4, 10)
        val allByMusic = mai2.userMusicDetail.findByUserId(rivalId)
            .asSequence()
            .filter { it.level in levels }
            .groupBy { it.musicId }
            .toSortedMap()
        val offset = ((data["nextIndex"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val pageSize = 300
        val page = allByMusic.entries.drop(offset).take(pageSize)
        val music = page.map { (musicId, rows) ->
            UserRivalMusic(musicId, rows.sortedBy { it.level }.map {
                UserRivalMusicDetail(it.level, it.achievement, it.deluxscoreMax, it.comboStatus)
            }.toMutableList())
        }
        val nextIndex = if (offset + page.size < allByMusic.size) offset + page.size else 0
        return mapOf("userId" to extId, "rivalId" to rivalId, "nextIndex" to nextIndex, "userRivalMusicList" to music)
    }
}

@RestController
@API("api/game/maimai2")
class Mai2SocialApi(private val social: Mai2SocialService) {
    @API("social")
    suspend fun state(@RP token: String, @RP aimeId: String) = social.state(token, aimeId)

    @API("socialSearch")
    suspend fun search(@RP token: String, @RP aimeId: String, @RP username: String) = social.search(token, aimeId, username)

    @API("socialRequest")
    suspend fun request(@RP token: String, @RP aimeId: String, @RP username: String) = social.requestFriend(token, aimeId, username)

    @API("socialAccept")
    suspend fun accept(@RP token: String, @RP aimeId: String, @RP friendId: Long) = social.acceptFriend(token, aimeId, friendId)

    @API("socialReject")
    suspend fun reject(@RP token: String, @RP aimeId: String, @RP friendId: Long) = social.rejectFriend(token, aimeId, friendId)

    @API("socialRemove")
    suspend fun remove(@RP token: String, @RP aimeId: String, @RP friendId: Long) = social.removeFriend(token, aimeId, friendId)

    @API("socialRival")
    suspend fun rival(@RP token: String, @RP aimeId: String, @RP rivalId: Long, @RP isAdd: Boolean) =
        social.setRival(token, aimeId, rivalId, isAdd)
}
