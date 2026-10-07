package test

import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.net.games.mai2.Mai2SocialFriend
import icu.samnyan.aqua.net.games.mai2.Mai2SocialFriendRepo
import icu.samnyan.aqua.net.games.mai2.Mai2SocialService
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.*
import java.util.Optional

private class SocialProfileFixture {
    val services = mock(AquaUserServices::class.java)
    val jwt = mock(JWT::class.java)
    val cards = mock(CardRepository::class.java)
    val repos = mock(Mai2Repos::class.java, RETURNS_DEEP_STUBS)
    val friends = mock(Mai2SocialFriendRepo::class.java)
    val account = AquaNetUser(auId = 1, friendCode = "0000000000000001")
    val ghost = Card(extId = 100, luid = "account-card", isGhost = true, aquaUser = account)
    val physical = Card(extId = 101, luid = "physical-card", aquaUser = account)
    val profile = Mai2UserDetail(card = ghost, userName = "Player").apply { id = 649 }
    val other = Mai2UserDetail(userName = "Friend").apply { id = 650 }
    val service = Mai2SocialService(services, repos, friends)

    init {
        account.ghostCard = ghost
        `when`(services.jwt).thenReturn(jwt)
        `when`(services.cardRepo).thenReturn(cards)
        `when`(jwt.auth("test-token")).thenReturn(account)
        `when`(cards.findByLuid(ghost.luid)).thenReturn(ghost)
        `when`(cards.findByLuid(physical.luid)).thenReturn(physical)
        `when`(repos.userData.findByCardExtId(ghost.extId)).thenReturn(profile)
        `when`(repos.userData.findById(other.id)).thenReturn(Optional.of(other))
        `when`(repos.userGeneralData.findByUserAndPropertyKey(profile, "favorite_rival")).thenReturn(null)
        `when`(friends.findForUser(profile.id)).thenReturn(listOf(Mai2SocialFriend(
            userLowId = profile.id, userHighId = other.id,
            status = Mai2SocialFriend.ACCEPTED, requestedByUserId = profile.id,
        )))
    }
}

class Mai2SocialProfileTest : StringSpec({
    "linked physical card shows the same friends as the account card" {
        val f = SocialProfileFixture()
        f.service.state("test-token", f.physical.luid) shouldBe f.service.state("test-token", f.ghost.luid)
        verify(f.repos.userData, never()).findByCardExtId(f.physical.extId)
    }

    "a foreign card is rejected before reading the account profile" {
        val f = SocialProfileFixture()
        f.physical.aquaUser = AquaNetUser(auId = 2)
        shouldThrow<ApiException> { f.service.state("test-token", f.physical.luid) }.code shouldBe 403
        verify(f.repos.userData, never()).findByCardExtId(f.ghost.extId)
    }

    "an account without a maimai profile still returns a real missing profile error" {
        val f = SocialProfileFixture()
        `when`(f.repos.userData.findByCardExtId(f.ghost.extId)).thenReturn(null)
        shouldThrow<ApiException> { f.service.state("test-token", f.physical.luid) }.code shouldBe 404
        verifyNoInteractions(f.friends)
    }

    "invalid sessions cannot read friends" {
        val f = SocialProfileFixture()
        `when`(f.jwt.auth("expired-token")).thenThrow(ApiException(400, "Invalid token"))
        shouldThrow<ApiException> { f.service.state("expired-token", f.ghost.luid) }.code shouldBe 400
        verifyNoInteractions(f.cards, f.friends)
    }
})
