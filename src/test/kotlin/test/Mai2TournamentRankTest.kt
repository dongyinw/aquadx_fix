package test

import icu.samnyan.aqua.sega.maimai2.Mai2TournamentDef
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService.Companion.rank
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService.Play
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Mai2TournamentRankTest : StringSpec({
    val songs = listOf(11810, 11745, 12025)
    fun play(user: Long, login: Long, music: Int, ach: Int, level: Int = 3, dx: Int = 1000, date: String = "2026-10-30 12:00:00") =
        Play(user, login, music, level, ach, dx, date)

    "credits missing a tournament song are ignored" {
        rank(listOf(play(1, 100, 11810, 1005000), play(1, 100, 11745, 1005000)), songs) shouldBe emptyList()
    }

    "best credit per player, ordered by total score" {
        val plays = listOf(
            // player 1, credit A: 300.0000%
            play(1, 100, 11810, 1000000), play(1, 100, 11745, 1000000), play(1, 100, 12025, 1000000),
            // player 1, credit B: better
            play(1, 200, 11810, 1010000), play(1, 200, 11745, 1000000), play(1, 200, 12025, 1000000),
            // player 2: one EXPERT chart (-1%)
            play(2, 300, 11810, 1010000, level = 2), play(2, 300, 11745, 1010000), play(2, 300, 12025, 1005000),
        )
        val r = rank(plays, songs)
        r.map { it.userId } shouldBe listOf(2L, 1L)
        r[0].totalScore shouldBe 3015000L // 1000000 + 1010000 + 1005000
        r[1].totalScore shouldBe 3010000L // 1010000 + 1000000 + 1000000
    }

    "ties are broken by DX score, then earliest play" {
        val plays = listOf(
            play(1, 1, 11810, 1000000, dx = 10), play(1, 1, 11745, 1000000), play(1, 1, 12025, 1000000),
            play(2, 2, 11810, 1000000, dx = 20), play(2, 2, 11745, 1000000), play(2, 2, 12025, 1000000),
            play(3, 3, 11810, 1000000, dx = 20, date = "2026-10-29 08:00:00"),
            play(3, 3, 11745, 1000000, date = "2026-10-29 08:00:00"), play(3, 3, 12025, 1000000, date = "2026-10-29 08:00:00"),
        )
        rank(plays, songs).map { it.userId } shouldBe listOf(3L, 2L, 1L)
    }

    "tournament expires after notice end date, falling back to end date" {
        val t = Mai2TournamentDef().apply { endDate = "2026-11-24 23:59:59" }
        t.isExpired("2026-11-24 23:59:59") shouldBe false
        t.isExpired("2026-11-25 00:00:00") shouldBe true
        t.noticeEndDate = "2026-11-30 23:59:59"
        t.isExpired("2026-11-25 00:00:00") shouldBe false
        t.isExpired("2026-12-01 00:00:00") shouldBe true
        Mai2TournamentDef().isExpired("2099-01-01 00:00:00") shouldBe false
    }
})
