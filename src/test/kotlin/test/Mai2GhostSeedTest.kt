package test

import icu.samnyan.aqua.sega.maimai2.Mai2GhostSeed
import icu.samnyan.aqua.sega.maimai2.Mai2GhostSeed.Play
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhostEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun play(music: Int, level: Int = 3, ach: Int = 990000, date: String = "2026-11-20 10:00:00.0") = Play(music, level, ach, date, 291)

class Mai2GhostSeedTest : StringSpec({
    "only the newest play of each song and difficulty is picked, newest first" {
        val plays = listOf(
            play(1, date = "2026-11-20 12:00:00.0", ach = 980000),
            play(2, date = "2026-11-20 11:00:00.0"),
            play(1, date = "2026-11-19 12:00:00.0", ach = 999000), // older play of the same song: ignored
            play(1, level = 2, date = "2026-11-19 11:00:00.0"),    // same song, other difficulty: kept
        )
        val picked = Mai2GhostSeed.pick(plays, 10)
        picked.map { it.musicId to it.level } shouldBe listOf(1 to 3, 2 to 3, 1 to 2)
        picked.first().achievement shouldBe 980000
    }

    "at most the requested number of songs are picked" {
        Mai2GhostSeed.pick((1..40).map { play(it) }, 15).size shouldBe 15
    }

    "utage songs, impossible difficulties and zero scores are skipped" {
        val picked = Mai2GhostSeed.pick(listOf(play(100001), play(5, level = 9), play(6, ach = 0), play(7)), 10)
        picked.map { it.musicId } shouldBe listOf(7)
    }

    "a ghost made from a play carries the player and the score but no recording" {
        val profile = Mai2UserDetail(userName = "Mai", iconId = 11, plateId = 22, titleId = 33, playerRating = 14500, classRank = 5, courseRank = 3, lastRegionId = 7)
        val entry = Mai2GhostSeed.build(profile, classValue = 1234, udemaeRate = 1500, play = play(8, level = 3, ach = 1005000), existing = null)!!
        entry.name shouldBe "Mai"
        entry.iconId shouldBe 11
        entry.rate shouldBe 14500
        entry.classValue shouldBe 1234
        entry.udemaeRate shouldBe 1500
        entry.musicId shouldBe 8
        entry.difficulty shouldBe 3
        entry.typeId shouldBe 3
        entry.achievement shouldBe 1005000
        entry.resultBitList shouldBe ""
        entry.resultNum shouldBe 0
        entry.regionCode shouldBe 7
        entry.shopId shouldBe 291
    }

    "a score-only ghost updates an older score-only ghost" {
        val old = Mai2UserGhostEntry().also { it.musicId = 8; it.difficulty = 3; it.achievement = 900000; it.resultNum = 0 }
        val entry = Mai2GhostSeed.build(Mai2UserDetail(), 0, 0, play(8, ach = 950000), old)!!
        (entry === old) shouldBe true
        entry.achievement shouldBe 950000
    }

    "a real recording is never replaced by a score-only ghost" {
        val recorded = Mai2UserGhostEntry().also { it.musicId = 8; it.difficulty = 3; it.achievement = 900000; it.resultNum = 812; it.resultBitList = "1,2,3" }
        Mai2GhostSeed.build(Mai2UserDetail(), 0, 0, play(8, ach = 1000000), recorded) shouldBe null
        recorded.achievement shouldBe 900000
    }
})
