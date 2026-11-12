package test

import icu.samnyan.aqua.sega.maimai2.Mai2FriendSeason
import icu.samnyan.aqua.sega.maimai2.Mai2VsGhost
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserFriendSeasonRanking
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhost
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhostEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun ghost(userId: Long, rate: Int, music: Int = 100, played: String = "2026-11-12 10:00:00.0") =
    Mai2UserGhostEntry().apply {
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

    "season rank is one more than the players ahead, and zero without points" {
        val row = Mai2UserFriendSeasonRanking().apply { seasonId = 7; point = 120; rewardGet = false }
        Mai2FriendSeason.withRank(row) { 0L }["rank"] shouldBe 1
        Mai2FriendSeason.withRank(row) { 4L }["rank"] shouldBe 5
        Mai2FriendSeason.withRank(row.apply { point = 0 }) { 9L }["rank"] shouldBe 0
    }
})
