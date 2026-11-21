package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaNetUserRepo
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import org.springframework.web.bind.annotation.RestController

/**
 * Admins bind a card to a user: enter the card number and the user name. Works like the user's own "link card" but
 * the admin picks the user, may take a card over from another user, and sees what is on the card first.
 */
@RestController
@API("/api/v2/admin/card-bind")
class AdminCardBindApi(
    private val jwt: JWT,
    private val userRepo: AquaNetUserRepo,
    private val cardService: CardService,
    private val cardRepository: CardRepository,
    private val cardGames: CardGameService,
    private val fedy: Fedy,
) {
    companion object {
        private val log = logger()
        /** The games whose data can move from a card to the account's virtual card. */
        val GAMES = listOf("mai2" to "舞萌 DX", "chu3" to "中二节奏", "ongeki" to "音击", "wacca" to "WACCA")
        private val ACCESS_CODE = Regex("""^\d{20}$""")
    }

    private fun admin(token: Str): AquaNetUser = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    private fun user(name: Str): AquaNetUser = userRepo.findByUsernameIgnoreCase(name.trim()) ?: (404 - "用户「${name.trim()}」不存在")

    /** Accepts a 20 digit access code (spaces allowed) or a colon separated Felica IDm, for a card that does not exist yet. */
    private fun normalize(raw: Str): Str {
        val id = raw.trim()
        if (":" in id) return cardService.sanitizeCardId(id)
        val digits = id.replace(" ", "")
        if (!ACCESS_CODE.matches(digits)) 400 - "卡号格式不正确：需要 20 位数字（Aime 卡号），或带冒号的 Felica IDm"
        return digits
    }

    private fun cardView(card: Card) = mapOf(
        "id" to card.id,
        "luid" to card.luid,
        "extId" to card.extId,
        "isGhost" to card.isGhost,
        "owner" to card.aquaUser?.username,
        "ownerAuId" to card.aquaUser?.auId,
    )

    @API("/preview")
    @Doc("What binding this card to this user would do: who owns the card now and which game data is on the card and on the account.", "Preview")
    suspend fun preview(@RP token: Str, @RP cardId: Str, @RP username: Str): Map<String, Any?> {
        admin(token)
        val target = user(username)
        val card = cardService.tryLookup(cardId)
        val onCard = card?.let { cardGames.getSummary(it) }
        val onAccount = cardGames.getSummary(target.ghostCard)
        return mapOf(
            "user" to mapOf("auId" to target.auId, "username" to target.username, "displayName" to target.displayName, "cardCount" to target.cards.size),
            "card" to card?.let(::cardView),
            "newCardId" to if (card == null) normalize(cardId) else null,
            "games" to GAMES.map { (game, label) -> mapOf("game" to game, "label" to label, "onCard" to onCard?.get(game), "onAccount" to onAccount[game]) },
        )
    }

    @API("/bind")
    @Doc("Bind a card to a user. migrate lists the games whose data moves to the user's virtual card; data of the other games is detached from the card.", "Result")
    suspend fun bind(
        @RP token: Str,
        @RP cardId: Str,
        @RP username: Str,
        @RP migrate: Str,
        @RP(required = false) force: Boolean?,
    ): Map<String, Any?> {
        val actor = admin(token)
        val target = user(username)
        val games = migrate.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (games.any { g -> GAMES.none { it.first == g } }) 400 - "不支持的游戏：${games.joinToString()}"

        val card = cardService.tryLookup(cardId)
        if (card == null) {
            // A card nobody has used yet: just create it for this user.
            val created = cardService.registerByAccessCode(normalize(cardId), target)
            fedy.onCardLinked(created.luid, oldExtId = null, ghostExtId = target.ghostCard.extId, emptyList())
            log.info("Admin {} created card {} for user {}", actor.auId, created.luid, target.username)
            return mapOf("success" to true, "created" to true, "card" to cardView(created), "migrated" to emptyList<Str>())
        }

        if (card.isGhost) 400 - "不能绑定账户虚拟卡"
        val owner = card.aquaUser
        if (owner?.auId == target.auId) 400 - "这张卡已经绑定在「${target.username}」名下"
        var previousOwner: Str? = null
        if (owner != null) {
            if (force != true) 409 - "这张卡已经绑定给用户「${owner.username}」，如需转移给「${target.username}」，请勾选「强制转移」"
            previousOwner = owner.username
            card.aquaUser = null
            async { cardRepository.save(card) }
            fedy.onCardUnlinked(card.luid)
        }

        card.aquaUser = target
        async { cardRepository.save(card) }
        cardGames.migrate(card, games)
        fedy.onCardLinked(card.luid, oldExtId = card.extId, ghostExtId = target.ghostCard.extId, games)
        log.info("Admin {} bound card {} to user {} (migrated {}, previous owner {})", actor.auId, card.luid, target.username, games, previousOwner)
        return mapOf("success" to true, "created" to false, "card" to cardView(card), "migrated" to games, "previousOwner" to previousOwner)
    }
}
