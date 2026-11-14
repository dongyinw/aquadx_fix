package test

import icu.samnyan.aqua.net.AnnouncementService
import icu.samnyan.aqua.net.NetAnnouncement
import icu.samnyan.aqua.net.games.GameTournamentService
import icu.samnyan.aqua.net.games.GameTournamentService.Raw
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

private fun at(minute: Int, hour: Int = 12) = LocalDateTime.of(2026, 11, 12, hour, minute)
private fun raw(user: Long, music: Int, score: Int, track: Int, time: LocalDateTime, level: Int = 3) =
    Raw(user, music, level, score, 100, track, time)

class GameTournamentTest : StringSpec({
    "plays with rising track numbers close together are one credit" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 990000, 1, at(0)), raw(1, 20, 980000, 2, at(5)), raw(1, 30, 970000, 3, at(10)),
        ))
        plays.map { it.loginDate }.distinct().size shouldBe 1
    }

    "the track number starting over begins a new credit" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 990000, 1, at(0)), raw(1, 20, 980000, 2, at(5)),
            raw(1, 10, 995000, 1, at(12)), raw(1, 20, 985000, 2, at(17)),
        ))
        plays.map { it.loginDate }.distinct().size shouldBe 2
    }

    "a long pause begins a new credit even when the track numbers keep rising" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 990000, 1, at(0)), raw(1, 20, 980000, 2, at(5), ),
            raw(1, 30, 970000, 3, at(30)),
        ))
        plays.map { it.loginDate }.distinct().size shouldBe 2
    }

    "unknown track numbers only use the pause rule" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 990000, 0, at(0)), raw(1, 20, 980000, 0, at(4)), raw(1, 30, 970000, 0, at(8)),
        ))
        plays.map { it.loginDate }.distinct().size shouldBe 1
    }

    "different players never share a credit" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 990000, 1, at(0)), raw(2, 10, 990000, 1, at(1)),
        ))
        plays.map { it.loginDate }.distinct().size shouldBe 2
    }

    "the best complete credit is ranked, like maimai DX" {
        val plays = GameTournamentService.toPlays(listOf(
            // player 1: the first credit lacks a song, the second has all three
            raw(1, 10, 1000000, 1, at(0)), raw(1, 20, 1000000, 2, at(5)),
            raw(1, 10, 900000, 1, at(10, hour = 14)), raw(1, 20, 900000, 2, at(15, hour = 14)), raw(1, 30, 900000, 3, at(20, hour = 14)),
            // player 2: one credit with all three songs, a better total
            raw(2, 10, 950000, 1, at(0)), raw(2, 20, 950000, 2, at(5)), raw(2, 30, 950000, 3, at(10)),
        ))
        val ranked = Mai2TournamentService.rank(plays, listOf(10, 20, 30))
        ranked.map { it.userId } shouldBe listOf(2L, 1L)
        ranked.first().totalScore shouldBe 2850000
    }

    "easier difficulties are penalised like in maimai DX" {
        val plays = GameTournamentService.toPlays(listOf(
            raw(1, 10, 1000000, 1, at(0), level = 0), // BASIC: -30000
        ))
        Mai2TournamentService.rank(plays, listOf(10)).first().totalScore shouldBe 970000
    }

    "an announcement is shown only inside its time window while enabled" {
        val a = NetAnnouncement(startDate = "2026-11-12 10:00:00", endDate = "2026-11-12 20:00:00")
        a.isActive("2026-11-12 09:59:59") shouldBe false
        a.isActive("2026-11-12 10:00:00") shouldBe true
        a.isActive("2026-11-12 20:00:00") shouldBe true
        a.isActive("2026-11-12 20:00:01") shouldBe false
        NetAnnouncement(enabled = false).isActive("2026-11-12 12:00:00") shouldBe false
        NetAnnouncement().isActive("2030-01-01 00:00:00") shouldBe true
        AnnouncementService.now().length shouldBe 19
    }
})
