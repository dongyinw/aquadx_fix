package test

import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.handler.UpsertUserAllHandler
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserDataRepo
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.util.BasicMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private fun savePortraitChoice(savedIcon: Int, uploadedIcon: Int): Mai2UserDetail {
    val userId = 123456789L
    val current = Mai2UserDetail(
        card = Card().apply { extId = userId },
        userName = "TEST",
        iconId = savedIcon,
        plateId = 600106,
        titleId = 6154,
        partnerId = 16,
        frameId = 600107,
        playCount = 594,
    )
    val userRepo = mock(Mai2UserDataRepo::class.java)
    val repos = mock(Mai2Repos::class.java)
    `when`(repos.userData).thenReturn(userRepo)
    `when`(userRepo.findByCardExtId(userId)).thenReturn(current)
    doAnswer { it.getArgument<Mai2UserDetail>(0) }
        .`when`(userRepo).saveAndFlush(any(Mai2UserDetail::class.java))

    val handler = UpsertUserAllHandler(BasicMapper(), mock(CardService::class.java), repos)
    handler.handle(mapOf(
        "userId" to userId,
        "upsertUserAll" to mapOf(
            "userData" to listOf(mapOf(
                "userName" to "TEST",
                "iconId" to uploadedIcon,
                "plateId" to 1,
                "titleId" to 1,
                "partnerId" to 38,
                "frameId" to 1,
                "playCount" to 595,
            )),
        ),
    )) shouldBe UpsertUserAllHandler.SUCCESS

    val captured = ArgumentCaptor.forClass(Mai2UserDetail::class.java)
    verify(userRepo).saveAndFlush(captured.capture())
    return captured.value
}

class Mai2CustomPortraitSaveTest : StringSpec({
    "game settlement saves custom portrait icon 10 instead of retaining the previous icon" {
        val saved = savePortraitChoice(706605, 10)

        saved.iconId shouldBe 10
        saved.playCount shouldBe 595
        saved.plateId shouldBe 600106
        saved.titleId shouldBe 6154
        saved.partnerId shouldBe 16
        saved.frameId shouldBe 600107
    }

    "game settlement can switch back from a custom portrait to a regular icon" {
        savePortraitChoice(10, 706605).iconId shouldBe 706605
    }

    "an empty icon upload still preserves the saved icon" {
        savePortraitChoice(706605, 0).iconId shouldBe 706605
        savePortraitChoice(10, 0).iconId shouldBe 10
    }
})
