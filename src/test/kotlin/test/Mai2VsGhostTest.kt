package test

import icu.samnyan.aqua.sega.maimai2.Mai2FriendSeason
import icu.samnyan.aqua.sega.maimai2.Mai2GhostServed
import icu.samnyan.aqua.sega.maimai2.Mai2VsGhost
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserFriendSeasonRanking
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhost
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhostEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun ghost(userId: Long, rate: Int, music: Int = 100, played: String = "2026-11-12 10:00:00.0", ghostId: Long = userId * 1000 + music) =
    Mai2UserGhostEntry().apply {
        id = ghostId
        user = Mai2UserDetail().apply { id = userId }
        this.rate = rate
        musicId = music
        playDatetime = played
        resultBitList = "1,2,255"
    }

class Mai2VsGhostTest : StringSpec({
    "bits survive the round trip, bytes above 127 stay unsigned" {
        val text = Mai2VsGhost.encodeBits(listOf(1.toByte(), 200.toByte(), 255.toByte()))
        text shouldBe "1,200,255"
        Mai2VsGhost.decodeBits(text) shouldBe listOf(1, 200, 255)
        Mai2VsGhost.decodeBits("") shouldBe emptyList()
    }

    "an upload without result bits or music is ignored" {
        val user = Mai2UserDetail().apply { id = 1 }
        Mai2VsGhost.toEntry(user, Mai2UserGhost().apply { musicId = 5 }, null) shouldBe null
        Mai2VsGhost.toEntry(user, Mai2UserGhost().apply { resultBitList = listOf(1) }, null) shouldBe null
    }

    "an upload for the same song updates the existing row" {
        val user = Mai2UserDetail().apply { id = 1 }
        val old = ghost(1, 1000)
        val up = Mai2UserGhost().apply { musicId = 100; difficulty = 3; achievement = 990000; resultBitList = listOf(9) }
        val entry = Mai2VsGhost.toEntry(user, up, old)!!
        (entry === old) shouldBe true
        entry.achievement shouldBe 990000
        entry.resultBitList shouldBe "9"
    }

    "closest rated players come first, one ghost per player" {
        val recent = listOf(
            ghost(1, 15000), ghost(2, 10100), ghost(2, 10100, music = 200), ghost(3, 12000),
        )
        val picked = Mai2VsGhost.select(recent, myRating = 10000, limit = 3)
        picked.map { it.user.id } shouldBe listOf(2L, 3L, 1L)
    }

    "more songs of the same players fill the list when there are few players" {
        val recent = listOf(ghost(1, 10000, 100), ghost(1, 10000, 200), ghost(2, 10500, 300))
        val picked = Mai2VsGhost.select(recent, myRating = 10000, limit = 15)
        picked.size shouldBe 3
        picked.first().user.id shouldBe 1L
    }

    "never more than the limit" {
        val recent = (1L..40L).map { ghost(it, 10000 + it.toInt()) }
        Mai2VsGhost.select(recent, 10000).size shouldBe Mai2VsGhost.MAX_RETURNED
    }

    "ghosts already given to the other player of the cabinet are skipped" {
        val recent = (1L..4L).map { ghost(it, 10000 + it.toInt()) }
        val first = Mai2VsGhost.select(recent, 10000, limit = 2)
        first.map { it.user.id } shouldBe listOf(1L, 2L)
        val second = Mai2VsGhost.select(recent, 10000, first.map { it.id }.toSet(), limit = 2)
        second.map { it.user.id } shouldBe listOf(3L, 4L)
    }

    "other players come first, then other songs of the same players, then the avoided ghosts" {
        val a1 = ghost(1, 10000, music = 100); val a2 = ghost(1, 10000, music = 200)
        val b1 = ghost(2, 10010, music = 100); val c1 = ghost(3, 10020, music = 100)
        val recent = listOf(a1, a2, b1, c1)
        // limit 2: the two players that were not avoided
        Mai2VsGhost.select(recent, 10000, avoid = setOf(a1.id), limit = 2).map { it.user.id } shouldBe listOf(2L, 3L)
        // limit 4: then the other song of the avoided player, then the avoided ghost itself
        Mai2VsGhost.select(recent, 10000, avoid = setOf(a1.id), limit = 4).map { it.id } shouldBe
            listOf(b1.id, c1.id, a2.id, a1.id)
    }

    "avoided ghosts are still used when there are not enough others" {
        val recent = listOf(ghost(1, 10000), ghost(2, 10010))
        val picked = Mai2VsGhost.select(recent, 10000, avoid = setOf(recent[0].id), limit = 15)
        picked.map { it.user.id } shouldBe listOf(2L, 1L)
    }

    "the cabinet record only affects other players and only for a short time" {
        val served = Mai2GhostServed(windowMillis = 1000, ownWindowMillis = 10_000)
        served.record("1.2.3.4", userId = 1, ids = setOf(10L, 11L), now = 0)
        served.avoid("1.2.3.4", userId = 2, now = 500) shouldBe setOf(10L, 11L)
        served.avoid("5.6.7.8", userId = 2, now = 500) shouldBe emptySet()   // another cabinet
        served.avoid("1.2.3.4", userId = 2, now = 1500) shouldBe emptySet()  // too long ago
    }

    "a player is not given the same ghosts again at the next credit" {
        val served = Mai2GhostServed(windowMillis = 1000, ownWindowMillis = 10_000)
        served.record("1.2.3.4", userId = 1, ids = setOf(10L, 11L), now = 0)
        served.avoid("1.2.3.4", userId = 1, now = 5000) shouldBe setOf(10L, 11L)  // same player, later
        served.avoid("9.9.9.9", userId = 1, now = 5000) shouldBe setOf(10L, 11L)  // even from another cabinet
        served.avoid("1.2.3.4", userId = 1, now = 20_000) shouldBe emptySet()      // forgotten after a while
    }

    "season rank is one more than the players ahead, and zero without points" {
        val row = Mai2UserFriendSeasonRanking().apply { seasonId = 7; point = 120; rewardGet = false }
        Mai2FriendSeason.withRank(row) { 0L }["rank"] shouldBe 1
        Mai2FriendSeason.withRank(row) { 4L }["rank"] shouldBe 5
        Mai2FriendSeason.withRank(row.apply { point = 0 }) { 9L }["rank"] shouldBe 0
    }
})
