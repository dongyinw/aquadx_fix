package icu.samnyan.aqua.net.games.mai2

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.Mai2Claim
import icu.samnyan.aqua.sega.maimai2.Mai2ClaimService
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

/** "创哥理赔": manual score re-submission and MaiMile editing for the player's own cards. */
@RestController
@API("/api/v2/user/mai2-claim")
class Mai2ClaimApi(
    val jwt: JWT,
    val cardService: CardService,
    val repos: Mai2Repos,
    val claims: Mai2ClaimService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(Mai2ClaimApi::class.java)
    }

    private fun ownedProfile(u: AquaNetUser, cardId: Str): Mai2UserDetail {
        val card = cardService.tryLookup(cardId) ?: (404 - "Card not found")
        if (card.aquaUser?.auId != u.auId) 403 - "Card is not linked to your account"
        return repos.userData.findByCard(card) ?: (400 - "这张卡还没有舞萌 DX 的游戏数据")
    }

    @API("/status")
    @Doc("MaiMile and today's usage of the claim tools for one of the current user's cards.", "Claim status")
    fun status(@RP token: Str, @RP cardId: Str) = jwt.auth(token) { u ->
        claims.status(ownedProfile(u, cardId))
    }

    @API("/mile")
    @Doc("Set the MaiMile of one of the current user's cards.", "Claim status")
    @Transactional
    fun mile(@RP token: Str, @RP cardId: Str, @RP point: Int) = jwt.auth(token) { u ->
        val profile = ownedProfile(u, cardId)
        claims.claimMile(profile, point).also { log.info("Claim mile: user=${u.username} card=${profile.card?.extId} point=$point") }
    }

    @API("/score")
    @Doc("Re-submit the best result of a song for one of the current user's cards.", "Claim status")
    @Transactional
    fun score(
        @RP token: Str,
        @RP cardId: Str,
        @RP musicId: Int,
        @RP level: Int,
        @RP achievement: Int,
        @RP deluxscore: Int,
        @RP comboStatus: Int,
        @RP syncStatus: Int,
    ) = jwt.auth(token) { u ->
        val profile = ownedProfile(u, cardId)
        val input = Mai2Claim.ScoreInput(musicId, level, achievement, deluxscore, comboStatus, syncStatus)
        claims.claimScore(profile, input).also {
            log.info("Claim score: user=${u.username} card=${profile.card?.extId} music=$musicId lv=$level ach=$achievement")
        }
    }

    @API("/admin/log")
    @Doc("Recent uses of the claim tools, for admins.", "Claim log")
    fun adminLog(@RP token: Str): List<Map<String, Any?>> {
        jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }
        return claims.recent()
    }
}
