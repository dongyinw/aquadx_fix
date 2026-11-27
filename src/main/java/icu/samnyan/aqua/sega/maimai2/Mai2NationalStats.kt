package icu.samnyan.aqua.sega.maimai2

import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalData

/**
 * Builds the answer for GetGameNationalData.
 *
 * The bundled snapshot (game_national_data.json) is a fixed list of songs and charts. It never learns about new
 * songs, so the catalog is the union of the snapshot and the music metadata. The rates are purely the local
 * players' best scores: a chart nobody played, or nobody reached a rank on, is 0%, and a single player can make a
 * rate 0% or 100%.
 */
object Mai2NationalStats {
    /** The levels the game shows national rates for: Expert, Master, Re:Master. */
    val DEFAULT_LEVELS = setOf(2, 3, 4)

    /** Utage charts (id >= 100000) only have level 0, so the game never asks for them. */
    const val UTAGE_MIN_ID = 100_000

    /** Local best score of one player on one chart, reduced to what the rates need. */
    data class Best(val achievement: Int, val comboStatus: Int)

    private fun rate(count: Int, total: Int) =
        if (total == 0) 0 else (count.toLong() * 1_000_000L / total).toInt()

    /**
     * @param snapshot the bundled national data, used for which songs and charts exist
     * @param levelCounts musicId -> number of charts the music has (from the music metadata; may be empty)
     * @param bests (musicId, level) -> best score of every player that played the chart
     */
    fun build(
        snapshot: List<Mai2NationalData>,
        levelCounts: Map<Int, Int>,
        bests: Map<Pair<Int, Int>, List<Best>>,
        requestedLevels: Set<Int>
    ): List<Map<String, Any>> {
        val charts = sortedMapOf<Int, MutableSet<Int>>()
        snapshot.forEach { e -> charts.getOrPut(e.musicId.toInt()) { sortedSetOf() }.addAll(e.nationalDataDetailList.map { it.level }) }
        levelCounts.forEach { (id, count) ->
            if (id >= UTAGE_MIN_ID) return@forEach
            charts.getOrPut(id) { sortedSetOf() }.addAll((0 until count).filter { it in DEFAULT_LEVELS })
        }

        return charts.mapNotNull { (id, levels) ->
            val details = levels.filter { it in requestedLevels }.map { level ->
                val rows = bests[id to level].orEmpty()
                val total = rows.size
                mapOf(
                    "level" to level,
                    "clearRateAp" to rate(rows.count { it.comboStatus >= 3 }, total),
                    "clearRateS" to rate(rows.count { it.achievement >= 970_000 }, total),
                    "clearRateSs" to rate(rows.count { it.achievement >= 990_000 }, total),
                    "clearRateSss" to rate(rows.count { it.achievement >= 1_000_000 }, total),
                    "clearRateSssPlus" to rate(rows.count { it.achievement >= 1_005_000 }, total)
                )
            }
            if (details.isEmpty()) null else mapOf("musicId" to id.toLong(), "nationalDataDetailList" to details)
        }
    }
}
