package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaNetUserRepo
import icu.samnyan.aqua.sega.allnet.KeychipSessionRepo
import icu.samnyan.aqua.sega.allnet.UserKeychip
import icu.samnyan.aqua.sega.allnet.UserKeychipRepo
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.model.CardStatus
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2ItemKind
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserItem
import icu.samnyan.aqua.sega.chusan.model.Chu3Repos
import icu.samnyan.aqua.sega.chusan.model.userdata.Chu3UserData
import icu.samnyan.aqua.sega.ongeki.OngekiRepos
import icu.samnyan.aqua.sega.ongeki.model.UserData as OngekiUserData
import icu.samnyan.aqua.net.games.IUserData
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

/** Administrative operations for the MikuNet web console. */
@RestController
@API("/api/v2/admin")
class AdminApi(
    val jwt: JWT,
    val userRepo: AquaNetUserRepo,
    val cardRepo: CardRepository,
    val userKeychipRepo: UserKeychipRepo,
    val keychipSessionRepo: KeychipSessionRepo,
    val mai2: Mai2Repos,
    val chu3: Chu3Repos,
    val ongeki: OngekiRepos,
    val accountDeletion: AccountDeletionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(AdminApi::class.java)
        private val keychipPattern = Regex("^[A-Z\\d]{15}$")
    }

    private fun admin(token: Str): AquaNetUser = jwt.auth(token).also {
        if (!it.isAdmin) 403 - "Admin permission required"
    }

    private fun user(auId: Long): AquaNetUser =
        userRepo.findByAuId(auId) ?: (404 - "User not found")

    private fun userSummary(u: AquaNetUser): Map<String, Any?> = mapOf(
        "auId" to u.auId,
        "username" to u.username,
        "displayName" to u.computedName,
        "email" to u.email,
        "lastLogin" to u.lastLogin,
        "regTime" to u.regTime,
        "keychipCount" to u.keychips.size,
        "isAdmin" to u.isAdmin,
    )

    private fun mai2Payload(profile: Mai2UserDetail): Map<String, Any?> = mapOf(
        "cardExtId" to profile.card?.extId,
        "userName" to profile.userName,
        "banState" to profile.banState,
        "iconId" to profile.iconId,
        "plateId" to profile.plateId,
        "titleId" to profile.titleId,
        "partnerId" to profile.partnerId,
        "frameId" to profile.frameId,
        "playerRating" to profile.playerRating,
        "highestRating" to profile.highestRating,
        "playCount" to profile.playCount,
        "firstPlayDate" to profile.firstPlayDate,
        "lastPlayDate" to profile.lastPlayDate,
        "lastClientId" to profile.lastClientId,
        "lastPlaceId" to profile.lastPlaceId,
        "lastPlaceName" to profile.lastPlaceName,
        "lastRomVersion" to profile.lastRomVersion,
        "lastDataVersion" to profile.lastDataVersion,
    )

    private fun cardSummary(card: Card, includeOwner: Boolean = false): Map<String, Any?> = mapOf(
        "id" to card.id,
        "luid" to card.luid,
        "extId" to card.extId,
        "registerTime" to card.registerTime,
        "accessTime" to card.accessTime,
        "isGhost" to card.isGhost,
        "rankingBanned" to card.rankingBanned,
        "status" to card.status.name,
        "owner" to if (includeOwner) card.aquaUser?.let(::userSummary) else null,
        "mai2" to mai2.userData.findByCardExtId(card.extId)?.let(::mai2Payload),
        "chu3" to chu3.userData.findByCard_ExtId(card.extId)?.let(::gamePayload),
        "ongeki" to ongeki.u.data.findByCard_ExtId(card.extId)?.let(::gamePayload),
    )

    private fun gamePayload(profile: IUserData): Map<String, Any?> = mapOf(
        "cardExtId" to profile.card?.extId,
        "userName" to profile.userName,
        "banState" to (profile as? Chu3UserData)?.banState,
        "playerRating" to profile.playerRating,
        "highestRating" to profile.highestRating,
        "playCount" to when (profile) {
            is Chu3UserData -> profile.playCount
            is OngekiUserData -> profile.playCount
            else -> null
        },
        "firstPlayDate" to profile.firstPlayDate,
        "lastPlayDate" to profile.lastPlayDate,
        "lastClientId" to profile.lastClientId,
        "lastRomVersion" to profile.lastRomVersion,
        "lastDataVersion" to null,
    )

    private fun itemPayload(profile: Mai2UserDetail) =
        mai2.userItem.findByUser(profile).map { item ->
            mapOf<String, Any?>(
                "itemKind" to item.itemKind,
                "itemId" to item.itemId,
                "stock" to item.stock,
                "isValid" to item.isValid,
            )
        }

    private fun recentPlaylogs(card: Card) =
        mai2.userPlaylog.findByUserCardExtId(
            card.extId,
            PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "userPlayDate")),
        ).content.map { playlog ->
            mapOf<String, Any?>(
                "id" to playlog.id,
                "playlogId" to playlog.playlogId,
                "placeId" to playlog.placeId,
                "placeName" to playlog.placeName,
                "loginDate" to playlog.loginDate,
                "playDate" to playlog.playDate,
                "userPlayDate" to playlog.userPlayDate,
                "musicId" to playlog.musicId,
                "level" to playlog.level,
                "achievement" to playlog.achievement,
                "afterRating" to playlog.afterRating,
            )
        }

    private fun mai2Profile(auId: Long): Mai2UserDetail? =
        cardRepo.findAllByAquaUserAuId(auId)
            .asSequence()
            .sortedBy { it.isGhost }
            .mapNotNull { mai2.userData.findByCardExtId(it.extId) }
            .firstOrNull()

    private fun chu3Profile(auId: Long): Chu3UserData? =
        cardRepo.findAllByAquaUserAuId(auId)
            .asSequence()
            .sortedBy { it.isGhost }
            .mapNotNull { chu3.userData.findByCard_ExtId(it.extId) }
            .firstOrNull()

    private fun ongekiProfile(auId: Long): OngekiUserData? =
        cardRepo.findAllByAquaUserAuId(auId)
            .asSequence()
            .sortedBy { it.isGhost }
            .mapNotNull { ongeki.u.data.findByCard_ExtId(it.extId) }
            .firstOrNull()

    private fun dateStart(value: String?): Long? = value?.trim()?.takeIf { it.isNotEmpty() }?.let {
        LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun dateEnd(value: String?): Long? = value?.trim()?.takeIf { it.isNotEmpty() }?.let {
        LocalDate.parse(it).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun inDateRange(value: Long, from: Long?, to: Long?) =
        (from == null || value >= from) && (to == null || value < to)

    @API("/users")
    @Transactional
    fun users(
        @RP token: Str,
        @RP query: Str,
        @RP(required = false) regFrom: Str?,
        @RP(required = false) regTo: Str?,
    ): List<Map<String, Any?>> {
        admin(token)
        val q = query.trim().lowercase()
        val from = dateStart(regFrom)
        val to = dateEnd(regTo)
        return userRepo.findAll().asSequence()
            .filter { u ->
                inDateRange(u.regTime, from, to) &&
                (q.isBlank() || listOf(
                    u.auId.toString(), u.username, u.displayName, u.email,
                    u.cards.joinToString(" ") { it.luid },
                    u.keychips.joinToString(" ") { it.keychipId },
                ).any { it.lowercase().contains(q) })
            }
            .take(100)
            .map(::userSummary)
            .toList()
    }

    @API("/cards")
    @Transactional
    fun cards(
        @RP token: Str,
        @RP query: Str,
        @RP(required = false) regFrom: Str?,
        @RP(required = false) regTo: Str?,
    ): List<Map<String, Any?>> {
        admin(token)
        val q = query.trim().lowercase()
        val from = dateStart(regFrom)
        val to = dateEnd(regTo)
        return cardRepo.findAll().asSequence()
            .filter { card ->
                val owner = card.aquaUser
                inDateRange(card.registerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), from, to) &&
                (q.isBlank() || listOf(
                    card.id.toString(), card.extId.toString(), card.luid, card.status.name,
                    owner?.auId?.toString().orEmpty(), owner?.username.orEmpty(), owner?.email.orEmpty(),
                ).any { it.lowercase().contains(q) })
            }
            .take(200)
            .map { cardSummary(it, includeOwner = true) }
            .toList()
    }

    @API("/card")
    @Transactional
    fun cardDetail(@RP token: Str, @RP cardId: Long): Map<String, Any?> {
        admin(token)
        val card = cardRepo.findById(cardId).orElse(null) ?: (404 - "Card not found")
        val profile = mai2.userData.findByCardExtId(card.extId)
        val owner = card.aquaUser
        return mapOf(
            "card" to cardSummary(card),
            "user" to owner?.let(::userSummary),
            "keychips" to (owner?.keychips?.map {
                mapOf<String, Any?>("id" to it.id, "keychipId" to it.keychipId, "enabled" to it.enabled)
            } ?: emptyList<Map<String, Any?>>()),
            "mai2" to profile?.let(::mai2Payload),
            "chu3" to chu3.userData.findByCard_ExtId(card.extId)?.let(::gamePayload),
            "ongeki" to ongeki.u.data.findByCard_ExtId(card.extId)?.let(::gamePayload),
            "items" to (profile?.let(::itemPayload) ?: emptyList<Map<String, Any?>>()),
            "playlogs" to recentPlaylogs(card),
        )
    }

    @API("/user")
    @Transactional
    fun userDetail(@RP token: Str, @RP auId: Long): Map<String, Any?> {
        admin(token)
        val u = user(auId)
        val cards = cardRepo.findAllByAquaUserAuId(auId)
        val mai2User = mai2Profile(auId)
        val chu3User = chu3Profile(auId)
        val ongekiUser = ongekiProfile(auId)

        return mapOf(
            "user" to mapOf(
                "auId" to u.auId,
                "username" to u.username,
                "email" to u.email,
                "displayName" to u.displayName,
                "computedName" to u.computedName,
                "country" to u.country,
                "region" to u.region,
                "profileLocation" to u.profileLocation,
                "profileBio" to u.profileBio,
                "emailConfirmed" to u.emailConfirmed,
                "canModifyKeychips" to u.canModifyKeychips,
                "isAdmin" to u.isAdmin,
                "lastLogin" to u.lastLogin,
                "regTime" to u.regTime,
            ),
            "cards" to cards.map { cardSummary(it) },
            "keychips" to u.keychips.map {
                mapOf("id" to it.id, "keychipId" to it.keychipId, "enabled" to it.enabled)
            },
            "mai2" to mai2User?.let(::mai2Payload),
            "chu3" to chu3User?.let(::gamePayload),
            "ongeki" to ongekiUser?.let(::gamePayload),
            "items" to (mai2User?.let(::itemPayload) ?: emptyList<Map<String, Any?>>()),
        )
    }

    @Transactional
    @API("/user/profile-set")
    fun profileSet(
        @RP token: Str,
        @RP auId: Long,
        @RP field: Str,
        @RP value: Str,
    ): Map<String, Any> {
        val actor = admin(token)
        val target = user(auId)
        when (field) {
            "displayName" -> target.displayName = value
            "profileLocation" -> target.profileLocation = value
            "profileBio" -> target.profileBio = value
            "country" -> target.country = value
            "region" -> target.region = value
            "emailConfirmed" -> target.emailConfirmed = parseBoolean(value)
            "canModifyKeychips" -> target.canModifyKeychips = parseBoolean(value)
            "isAdmin" -> {
                val enabled = parseBoolean(value)
                if (actor.auId == target.auId && !enabled) 400 - "You cannot remove your own admin permission"
                target.isAdmin = enabled
            }
            else -> 400 - "Unsupported profile field"
        }
        userRepo.save(target)
        log.info("Admin {} changed user {} field {}", actor.auId, target.auId, field)
        return mapOf("success" to true)
    }

    @Transactional
    @API("/user/game-profile-set")
    fun gameProfileSet(
        @RP token: Str,
        @RP auId: Long,
        @RP game: Str,
        @RP field: Str,
        @RP value: Str,
    ): Map<String, Any> {
        val actor = admin(token)
        val target = user(auId)
        when (game) {
            "mai2" -> {
                val data = mai2Profile(target.auId) ?: (404 - "Maimai profile not found")
                setMai2Field(data, field, value)
                mai2.userData.save(data)
            }
            "chu3" -> {
                val data = chu3Profile(target.auId) ?: (404 - "Chuni profile not found")
                setChu3Field(data, field, value)
                chu3.userData.save(data)
            }
            "ongeki" -> {
                val data = ongekiProfile(target.auId) ?: (404 - "Ongeki profile not found")
                if (field != "userName") 400 - "Unsupported Ongeki profile field"
                data.userName = value
                ongeki.u.data.save(data)
            }
            else -> 400 - "Unsupported game"
        }
        log.info("Admin {} changed {} profile {} field {}", actor.auId, game, target.auId, field)
        return mapOf("success" to true)
    }

    @Transactional
    @API("/card/game-profile-set")
    fun cardGameProfileSet(
        @RP token: Str,
        @RP cardId: Long,
        @RP game: Str,
        @RP field: Str,
        @RP value: Str,
    ): Map<String, Any> {
        val actor = admin(token)
        val card = cardRepo.findById(cardId).orElse(null) ?: (404 - "Card not found")
        when (game) {
            "mai2" -> {
                val data = mai2.userData.findByCardExtId(card.extId) ?: (404 - "Maimai profile not found")
                setMai2Field(data, field, value)
                mai2.userData.save(data)
            }
            "chu3" -> {
                val data = chu3.userData.findByCard_ExtId(card.extId) ?: (404 - "Chuni profile not found")
                setChu3Field(data, field, value)
                chu3.userData.save(data)
            }
            "ongeki" -> {
                val data = ongeki.u.data.findByCard_ExtId(card.extId) ?: (404 - "Ongeki profile not found")
                if (field != "userName") 400 - "Unsupported Ongeki profile field"
                data.userName = value
                ongeki.u.data.save(data)
            }
            else -> 400 - "Unsupported game"
        }
        log.info("Admin {} changed {} card {} field {}", actor.auId, game, card.id, field)
        return mapOf("success" to true)
    }

    private fun setMai2Field(data: Mai2UserDetail, field: Str, value: Str) {
        when (field) {
            "userName" -> data.userName = value
            "banState" -> data.banState = value.toIntOrNull() ?: (400 - "Invalid banState")
            "iconId" -> data.iconId = value.toIntOrNull() ?: (400 - "Invalid iconId")
            "plateId" -> data.plateId = value.toIntOrNull() ?: (400 - "Invalid plateId")
            "titleId" -> data.titleId = value.toIntOrNull() ?: (400 - "Invalid titleId")
            "partnerId" -> data.partnerId = value.toIntOrNull() ?: (400 - "Invalid partnerId")
            "frameId" -> data.frameId = value.toIntOrNull() ?: (400 - "Invalid frameId")
            else -> 400 - "Unsupported game profile field"
        }
    }

    private fun setChu3Field(data: Chu3UserData, field: Str, value: Str) {
        when (field) {
            "userName" -> data.userName = value
            "banState" -> data.banState = value.toIntOrNull() ?: (400 - "Invalid banState")
            else -> 400 - "Unsupported Chuni profile field"
        }
    }

    @Transactional
    @API("/user/item-grant")
    fun itemGrant(
        @RP token: Str,
        @RP auId: Long,
        @RP game: Str,
        @RP itemKind: Int,
        @RP itemId: Int,
        @RP amount: Int,
    ): Map<String, Any> {
        val actor = admin(token)
        val target = user(auId)
        if (game != "mai2") 400 - "Only mai2 is supported"
        val data = mai2Profile(target.auId) ?: (404 - "Maimai profile not found")
        val stock = grantMai2Item(data, itemKind, itemId, amount)
        log.info("Admin {} granted mai2 item {}/{} x{} to {}", actor.auId, itemKind, itemId, amount, target.auId)
        return mapOf("success" to true, "stock" to stock)
    }

    @Transactional
    @API("/card/item-grant")
    fun cardItemGrant(
        @RP token: Str,
        @RP cardId: Long,
        @RP game: Str,
        @RP itemKind: Int,
        @RP itemId: Int,
        @RP amount: Int,
    ): Map<String, Any> {
        val actor = admin(token)
        val card = cardRepo.findById(cardId).orElse(null) ?: (404 - "Card not found")
        if (game != "mai2") 400 - "Only mai2 is supported"
        val data = mai2.userData.findByCardExtId(card.extId) ?: (404 - "Maimai profile not found")
        val stock = grantMai2Item(data, itemKind, itemId, amount)
        log.info("Admin {} granted mai2 item {}/{} x{} to card {}", actor.auId, itemKind, itemId, amount, card.id)
        return mapOf("success" to true, "stock" to stock)
    }

    private fun grantMai2Item(data: Mai2UserDetail, itemKind: Int, itemId: Int, amount: Int): Int {
        if (Mai2ItemKind.ALL[itemKind] == null) 400 - "Invalid item kind"
        if (itemId < 0 || amount <= 0 || amount > 1_000_000) 400 - "Invalid item grant"
        val item = mai2.userItem.findByUserAndItemKindAndItemId(data, itemKind, itemId)
            ?: Mai2UserItem().apply {
                user = data
                this.itemKind = itemKind
                this.itemId = itemId
            }
        item.stock += amount
        item.isValid = true
        mai2.userItem.save(item)
        return item.stock
    }

    @Transactional
    @API("/user/keychip-set")
    fun keychipSet(@RP token: Str, @RP keychipId: Str, @RP enabled: Boolean): Map<String, Any> {
        val actor = admin(token)
        val keychip = userKeychipRepo.findByKeychipId(normalizeKeychip(keychipId))
            ?: (404 - "Keychip not found")
        keychip.enabled = enabled
        userKeychipRepo.save(keychip)
        if (!enabled) keychipSessionRepo.deleteAllByKeychipId(keychip.keychipId)
        log.info("Admin {} set keychip {} enabled={}", actor.auId, keychip.keychipId, enabled)
        return mapOf("success" to true, "enabled" to enabled)
    }

    @Transactional
    @API("/user/keychip-add")
    fun keychipAdd(@RP token: Str, @RP auId: Long, @RP keychipId: Str): Map<String, Any> {
        val actor = admin(token)
        val target = user(auId)
        val normalized = normalizeKeychip(keychipId)
        if (userKeychipRepo.existsByKeychipId(normalized)) 400 - "Keychip already exists"
        userKeychipRepo.save(UserKeychip(user = target, keychipId = normalized))
        log.info("Admin {} added keychip {} to user {}", actor.auId, normalized, target.auId)
        return mapOf("success" to true, "keychipId" to normalized)
    }

    @Transactional
    @API("/user/keychip-delete")
    fun keychipDelete(@RP token: Str, @RP keychipId: Str): Map<String, Any> {
        val actor = admin(token)
        val normalized = normalizeKeychip(keychipId)
        keychipSessionRepo.deleteAllByKeychipId(normalized)
        val keychip = userKeychipRepo.findByKeychipId(normalized) ?: (404 - "Keychip not found")
        userKeychipRepo.delete(keychip)
        log.info("Admin {} deleted keychip {}", actor.auId, normalized)
        return mapOf("success" to true)
    }

    @Transactional
    @API("/user/delete")
    fun userDelete(@RP token: Str, @RP auId: Long): Map<String, Any> {
        val actor = admin(token)
        if (actor.auId == auId) 400 - "You cannot delete your own account"
        val cleanup = accountDeletion.deleteDatabaseData(auId)
        accountDeletion.deleteUploadedFiles(cleanup)
        log.info("Admin {} deleted user {}", actor.auId, auId)
        return mapOf("success" to true)
    }

    @Transactional
    @API("/card/delete")
    fun cardDelete(@RP token: Str, @RP cardId: Long): Map<String, Any> {
        val actor = admin(token)
        val card = cardRepo.findById(cardId).orElse(null) ?: (404 - "Card not found")
        if (card.isGhost) 400 - "The ghost card can only be removed with its Net account"
        card.status = CardStatus.DELETED
        card.aquaUser = null
        cardRepo.saveAndFlush(card)
        log.info("Admin {} deleted card {}", actor.auId, card.id)
        return mapOf("success" to true)
    }

    private fun normalizeKeychip(value: Str): String {
        val normalized = value.trim().uppercase().replace("-", "")
        if (!keychipPattern.matches(normalized)) 400 - "Invalid keychip ID"
        return normalized
    }

    private fun parseBoolean(value: Str): Boolean = when (value.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> 400 - "Invalid boolean value"
    }
}
