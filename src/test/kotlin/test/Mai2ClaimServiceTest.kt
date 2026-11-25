package test

import icu.samnyan.aqua.net.games.GenericMusicMeta
import icu.samnyan.aqua.net.games.GenericNoteMeta
import icu.samnyan.aqua.net.games.mai2.Maimai2
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.maimai2.Mai2Claim
import icu.samnyan.aqua.sega.maimai2.Mai2ClaimService
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserClaimLogRepo
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserDataRepo
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserMusicDetailRepo
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserClaimLog
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers.any
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserMusicDetail
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

/** Mockito's any() returns null, which Kotlin refuses for non-null parameters; register the matcher but hand back a real value. */
private fun <T> anyOf(type: Class<T>, fallback: T): T { any(type); return fallback }
private fun anyUser() = anyOf(Mai2UserDetail::class.java, Mai2UserDetail())
private fun anyLog() = anyOf(Mai2UserClaimLog::class.java, Mai2UserClaimLog())
private fun anyMusic() = anyOf(Mai2UserMusicDetail::class.java, Mai2UserMusicDetail())

private class Fixture(usedScore: Long = 0, usedMile: Long = 0) {
    val logs = mock(Mai2UserClaimLogRepo::class.java)
    val users = mock(Mai2UserDataRepo::class.java)
    val music = mock(Mai2UserMusicDetailRepo::class.java)
    val repos = mock(Mai2Repos::class.java)
    val maimai2 = mock(Maimai2::class.java)
    val service: Mai2ClaimService

    init {
        `when`(repos.userClaimLog).thenReturn(logs)
        `when`(repos.userData).thenReturn(users)
        `when`(repos.userMusicDetail).thenReturn(music)
        `when`(maimai2.musicMapping).thenReturn(mapOf(8 to GenericMusicMeta("Song", "Ver1.00.00", List(4) { GenericNoteMeta(8.0) })))
        `when`(logs.countByUserAndKindAndCreatedAtGreaterThanEqual(anyUser(), anyString(), anyOf(LocalDateTime::class.java, LocalDateTime.now())))
            .thenAnswer { if (it.getArgument<String>(1) == Mai2Claim.KIND_SCORE) usedScore else usedMile }
        service = Mai2ClaimService(repos, maimai2, testSwitches(), scoreDailyLimit = 20, mileDailyLimit = 3)
    }
}

private fun player(point: Int = 500, ban: Int = 0) = Mai2UserDetail().also { it.point = point; it.totalPoint = 800; it.banState = ban }
private fun status(code: () -> Unit) = shouldThrow<ApiException> { code() }.code

class Mai2ClaimServiceTest : StringSpec({
    "setting the mile saves it, raises the lifetime total when needed and writes the audit log" {
        val f = Fixture()
        val user = player(point = 500)
        f.service.claimMile(user, 1200)
        user.point shouldBe 1200
        user.totalPoint shouldBe 1200
        verify(f.users).save(user)
        verify(f.logs).save(anyLog())
    }

    "the mile must stay in range, change, and within the daily limit" {
        status { Fixture().service.claimMile(player(), 100000) } shouldBe 400
        status { Fixture().service.claimMile(player(), -1) } shouldBe 400
        status { Fixture().service.claimMile(player(point = 500), 500) } shouldBe 400
        status { Fixture(usedMile = 3).service.claimMile(player(), 600) } shouldBe 429
    }

    "banned players cannot use the tools" {
        status { Fixture().service.claimMile(player(ban = 2), 600) } shouldBe 403
        status { Fixture().service.claimScore(player(ban = 1), Mai2Claim.ScoreInput(8, 3, 990000, 1500, 0, 0)) } shouldBe 403
    }

    "a valid score is saved and logged" {
        val f = Fixture()
        f.service.claimScore(player(), Mai2Claim.ScoreInput(8, 3, 990000, 1500, 1, 0))
        verify(f.music).save(anyMusic())
        verify(f.logs).save(anyLog())
    }

    "lowering a score is allowed and the audit log keeps the value from before" {
        val f = Fixture()
        val stored = icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserMusicDetail().also {
            it.musicId = 8; it.level = 3; it.achievement = 1005000; it.deluxscoreMax = 1500
        }
        `when`(f.music.findByUserAndMusicIdAndLevel(anyUser(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(stored)
        f.service.claimScore(player(), Mai2Claim.ScoreInput(8, 3, 980000, 1200, 0, 0))
        stored.achievement shouldBe 980000
        val captor = org.mockito.ArgumentCaptor.forClass(Mai2UserClaimLog::class.java)
        verify(f.logs).save(captor.capture() ?: Mai2UserClaimLog())
        captor.value.oldValue shouldBe 1005000
        captor.value.newValue shouldBe 980000
    }

    "the claim tools are refused while the admin has switched them off" {
        val f = Fixture()
        val off = Mai2ClaimService(f.repos, f.maimai2, testSwitches(icu.samnyan.aqua.net.Feature.CLAIM), scoreDailyLimit = 20, mileDailyLimit = 3)
        status { off.claimMile(player(), 600) } shouldBe 403
        status { off.claimScore(player(), Mai2Claim.ScoreInput(8, 3, 990000, 1500, 0, 0)) } shouldBe 403
        verify(f.music, never()).save(anyMusic())
    }

    "the daily score limit and invalid songs are refused without touching the profile" {
        val f = Fixture(usedScore = 20)
        status { f.service.claimScore(player(), Mai2Claim.ScoreInput(8, 3, 990000, 1500, 0, 0)) } shouldBe 429
        status { Fixture().service.claimScore(player(), Mai2Claim.ScoreInput(999, 3, 990000, 1500, 0, 0)) } shouldBe 400
        verify(f.music, never()).save(anyMusic())
    }
})
