package test

import icu.samnyan.aqua.sega.maimai2.Mai2NationalStats
import icu.samnyan.aqua.sega.maimai2.Mai2NationalStats.Best
import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalData
import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalDataDetail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeBetween

private fun detail(lv: Int, rate: Int) = Mai2NationalDataDetail().apply {
    level = lv; clearRateAp = rate / 2; clearRateS = rate; clearRateSs = rate; clearRateSss = rate; clearRateSssPlus = rate
}

private fun snap(id: Long, vararg d: Mai2NationalDataDetail) =
    Mai2NationalData().apply { musicId = id; nationalDataDetailList = d.toList() }

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.details() = this["nationalDataDetailList"] as List<Map<String, Any>>

class Mai2NationalStatsTest : StringSpec({
    val snapshot = listOf(snap(8, detail(2, 800_000), detail(3, 600_000)))
    val levels = Mai2NationalStats.DEFAULT_LEVELS

    "without local plays the snapshot values are returned unchanged" {
        val out = Mai2NationalStats.build(snapshot, emptyMap(), emptyMap(), levels)
        out.single().details().first { it["level"] == 3 }["clearRateSss"] shouldBe 600_000
    }

    "one player barely moves the rate instead of forcing 0% or 100%" {
        val bests = mapOf((8 to 3) to listOf(Best(1_010_000, 3)))
        val rate = Mai2NationalStats.build(snapshot, emptyMap(), bests, levels)
            .single().details().first { it["level"] == 3 }["clearRateSss"] as Int
        rate.shouldBeBetween(600_000, 650_000)
    }

    "local data dominates once many players have played" {
        val bests = mapOf((8 to 3) to List(2000) { Best(900_000, 0) })
        val rate = Mai2NationalStats.build(snapshot, emptyMap(), bests, levels)
            .single().details().first { it["level"] == 3 }["clearRateSss"] as Int
        rate.shouldBeBetween(0, 10_000)
    }

    "new songs from the metadata get entries using the level average as prior" {
        val out = Mai2NationalStats.build(snapshot, mapOf(8 to 4, 12058 to 4), emptyMap(), levels)
        val new = out.first { it["musicId"] == 12058L }
        new.details().map { it["level"] } shouldBe listOf(2, 3)
        new.details().first { it["level"] == 3 }["clearRateSss"] shouldBe 600_000
    }

    "charts missing from the snapshot are filled in for known songs" {
        val out = Mai2NationalStats.build(snapshot, mapOf(8 to 4), emptyMap(), levels)
        out.single().details().map { it["level"] } shouldBe listOf(2, 3)
    }

    "utage charts and unrequested levels are not returned" {
        val out = Mai2NationalStats.build(snapshot, mapOf(100018 to 1, 8 to 5), emptyMap(), setOf(3))
        out.map { it["musicId"] } shouldBe listOf(8L)
        out.single().details().map { it["level"] } shouldBe listOf(3)
    }
})
