package test

import ext.JACKSON
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.net.games.wacca.Wacca
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.wacca.model.db.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.*

private class WaccaExportFixture {
    val jwt = mock(JWT::class.java)
    val us = mock(AquaUserServices::class.java)
    val account = AquaNetUser(username = "owner")
    val repos = WaccaRepos(
        mock(WcUserRepo::class.java), mock(WcUserOptionRepo::class.java), mock(WcUserBingoRepo::class.java),
        mock(WcUserFriendRepo::class.java), mock(WcUserGateRepo::class.java), mock(WcUserItemRepo::class.java),
        mock(WcUserBestScoreRepo::class.java), mock(WcUserPlayLogRepo::class.java), mock(WcUserStageUpRepo::class.java),
    )
    val api = Wacca(us, repos.playLog, repos.user, repos.bestScore, repos)

    init {
        `when`(us.jwt).thenReturn(jwt)
        `when`(jwt.auth("owner-token")).thenReturn(account)
    }
}

class WaccaDataExportTest : StringSpec({
    "WACCA exports the authenticated player's profile, collections, scores and playlogs" {
        val f = WaccaExportFixture()
        val profile = WaccaUser().apply { userName = "owner-profile" }
        `when`(f.repos.user.findByCard(f.account.ghostCard)).thenReturn(profile)
        `when`(f.repos.option.findByUser(profile)).thenReturn(listOf(WcUserOption(3, 7)))
        `when`(f.repos.bingo.findByUser(profile)).thenReturn(listOf(WcUserBingo().apply { pageNumber = 2 }))
        `when`(f.repos.gate.findByUser(profile)).thenReturn(listOf(WcUserGate().apply { gateId = 9 }))
        `when`(f.repos.item.findByUser(profile)).thenReturn(listOf(WcUserItem(itemId = 12)))
        `when`(f.repos.bestScore.findByUser(profile)).thenReturn(listOf(WcUserScore().apply { musicId = 100; achievement = 990000 }))
        `when`(f.repos.playLog.findByUser(profile)).thenReturn(listOf(WcUserPlayLog().apply { musicId = 100; afterRating = 2345 }))
        `when`(f.repos.stageUp.findByUser(profile)).thenReturn(listOf(WcUserStageUp().apply { stageId = 4 }))
        `when`(f.repos.friend.findByUser(profile)).thenReturn(listOf(WcUserFriend().apply {
            with = WaccaUser().apply { id = 87; userName = "private-friend-profile" }
            isAccepted = true
        }))

        val json = JACKSON.readTree(JACKSON.writeValueAsString(f.api.exportUserData("owner-token")))
        json["gameId"].asText() shouldBe "SDFE"
        json.at("/userData/userName").asText() shouldBe "owner-profile"
        json.at("/userOptionList/0/value").asInt() shouldBe 7
        json.at("/userBingoList/0/pageNumber").asInt() shouldBe 2
        json.at("/userGateList/0/gateId").asInt() shouldBe 9
        json.at("/userItemList/0/itemId").asInt() shouldBe 12
        json.at("/userBestScoreList/0/achievement").asInt() shouldBe 990000
        json.at("/userPlaylogList/0/afterRating").asInt() shouldBe 2345
        json.at("/userStageUpList/0/stageId").asInt() shouldBe 4
        json["userFriendList"] shouldBe JACKSON.readTree("""[{"withUserId":87,"isAccepted":true}]""")
        json.toString().contains("private-friend-profile") shouldBe false
        verify(f.repos.user).findByCard(f.account.ghostCard)
    }

    "WACCA export requires authentication and reports a missing profile" {
        val f = WaccaExportFixture()
        `when`(f.jwt.auth("invalid-token")).thenThrow(ApiException(400, "Invalid token"))
        shouldThrow<ApiException> { f.api.exportUserData("invalid-token") }.code shouldBe 400
        verifyNoInteractions(f.repos.user)
        shouldThrow<ApiException> { f.api.exportUserData("owner-token") }.code shouldBe 404
        verifyNoInteractions(f.repos.bestScore, f.repos.playLog, f.repos.item)
    }
})
