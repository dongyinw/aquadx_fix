package test

import icu.samnyan.aqua.sega.maimai2.Mai2NationalStats
import icu.samnyan.aqua.sega.maimai2.Mai2NationalStats.Best
import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalData
import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalDataDetail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun detail(lv: Int) = Mai2NationalDataDetail().apply {
    level = lv; clearRateAp = 500_000; clearRateS = 800_000; clearRateSs = 800_000; clearRateSss = 800_000; clearRateSssPlus = 800_000
}

private fun snap(id: Long, vararg d: Mai2NationalDataDetail) =
    Mai2NationalData().apply { musicId = id; nationalDataDetailList = d.toList() }

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.details() = this["nationalDataDetailList"] as List<Map<String, Any>>

class Mai2NationalStatsTest : StringSpec({
    val snapshot = listOf(snap(8, detail(2), detail(3)))
    val levels = Mai2NationalStats.DEFAULT_LEVELS

    "a chart nobody played is 0% on every rate, whatever the snapshot says" {
        val d = Mai2NationalStats.build(snapshot, emptyMap(), emptyMap(), levels).single().details().first { it["level"] == 3 }
        listOf("clearRateAp", "clearRateS", "clearRateSs", "clearRateSss", "clearRateSssPlus").map { d[it] } shouldBe List(5) { 0 }
    }

    "one player who reached a rank makes it 100%, one who did not makes it 0%" {
        val bests = mapOf(
            (8 to 3) to listOf(Best(1_010_000, 3)),
            (8 to 2) to listOf(Best(900_000, 0))
        )
        val out = Mai2NationalStats.build(snapshot, emptyMap(), bests, levels).single().details()
        val master = out.first { it["level"] == 3 }
        master["clearRateSssPlus"] shouldBe 1_000_000
        master["clearRateAp"] shouldBe 1_000_000
        val expert = out.first { it["level"] == 2 }
        expert["clearRateS"] shouldBe 0
        expert["clearRateAp"] shouldBe 0
    }

    "rates are the share of players, thresholds are cumulative" {
        val bests = mapOf((8 to 3) to listOf(Best(1_006_000, 3), Best(991_000, 1), Best(960_000, 0), Best(800_000, 0)))
        val d = Mai2NationalStats.build(snapshot, emptyMap(), bests, levels).single().details().first { it["level"] == 3 }
        d["clearRateAp"] shouldBe 250_000
        d["clearRateS"] shouldBe 500_000
        d["clearRateSs"] shouldBe 500_000
        d["clearRateSss"] shouldBe 250_000
        d["clearRateSssPlus"] shouldBe 250_000
    }

    "new songs from the metadata get entries" {
        val out = Mai2NationalStats.build(snapshot, mapOf(8 to 4, 12058 to 4), emptyMap(), levels)
        out.first { it["musicId"] == 12058L }.details().map { it["level"] } shouldBe listOf(2, 3)
    }

    "charts missing from the snapshot are filled in for known songs" {
        val out = Mai2NationalStats.build(listOf(snap(8, detail(3))), mapOf(8 to 4), emptyMap(), levels)
        out.single().details().map { it["level"] } shouldBe listOf(2, 3)
    }

    "utage charts and unrequested levels are not returned" {
        val out = Mai2NationalStats.build(snapshot, mapOf(100018 to 1, 8 to 5), emptyMap(), setOf(3))
        out.map { it["musicId"] } shouldBe listOf(8L)
        out.single().details().map { it["level"] } shouldBe listOf(3)
    }
})
