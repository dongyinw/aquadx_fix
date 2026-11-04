package test

import icu.samnyan.aqua.net.games.mai2.circle.Mai2CircleMemberEntity
import icu.samnyan.aqua.net.games.mai2.circle.Mai2CircleMemberRepo
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.handler.UpsertUserAllHandler
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserDataRepo
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.util.BasicMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.*

class Mai2CirclePointUploadTest : StringSpec({
    "settlement saves only valid points for the player's current circle" {
        val userId = 123456789L
        val current = Mai2UserDetail(card = Card().apply { extId = userId }, userName = "TEST")
        val userRepo = mock(Mai2UserDataRepo::class.java)
        val repos = mock(Mai2Repos::class.java)
        val members = mock(Mai2CircleMemberRepo::class.java)
        val member = Mai2CircleMemberEntity(circleId = 42, aimeExtId = userId, point = 10)
        `when`(repos.userData).thenReturn(userRepo)
        `when`(userRepo.findByCardExtId(userId)).thenReturn(current)
        doAnswer { it.getArgument<Mai2UserDetail>(0) }.`when`(userRepo).saveAndFlush(any(Mai2UserDetail::class.java))
        `when`(members.findByAimeExtId(userId)).thenReturn(member)
        val handler = UpsertUserAllHandler(BasicMapper(), mock(CardService::class.java), repos, members)
        fun upload(points: List<Map<String, Any>>) = handler.handle(mapOf(
            "userId" to userId,
            "upsertUserAll" to mapOf("userData" to listOf(mapOf("userName" to "TEST")), "userCirclePointDataList" to points),
        ))
        upload(listOf(mapOf("circleId" to 42, "point" to 123)))
        member.point shouldBe 123
        upload(listOf(mapOf("circleId" to 99, "point" to 999), mapOf("circleId" to 42)))
        member.point shouldBe 123
        verify(members, times(1)).save(member)
    }
})
