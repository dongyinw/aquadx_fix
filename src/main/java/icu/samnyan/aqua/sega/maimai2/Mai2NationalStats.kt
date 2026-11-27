package icu.samnyan.aqua.sega.maimai2

import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalData
import icu.samnyan.aqua.sega.maimai2.model.Mai2NationalDataDetail

/**
 * Builds the answer for GetGameNationalData.
 *
 * The bundled snapshot (game_national_data.json) is a fixed list of real national rates. It never learns about new
 * songs, so the catalog is the union of the snapshot and the music metadata, and the values are the local players'
 * best scores blended with a prior, so one player cannot swing a song to 0% or 100%.
 */
object Mai2NationalStats {
    /** The levels the game shows national rates for: Expert, Master, Re:Master. */
    val DEFAULT_LEVELS = setOf(2, 3, 4)

    /** Utage charts (id >= 100000) only have level 0, so the game never asks for them. */
    const val UTAGE_MIN_ID = 100_000

    /**
     * Prior strength in "virtual players". With the prior worth PRIOR_WEIGHT players, a song played by one local
     * player is 1/(1+PRIOR_WEIGHT) local and the rest prior, and local data takes over as players accumulate.
     */
    const val PRIOR_WEIGHT = 20

    /** Local best scores of one chart, reduced to what the rates need. */
    data class Best(val achievement: Int, val comboStatus: Int)

    private const val SCALE = 1_000_000.0

    private fun Mai2NationalDataDetail.priors() = doubleArrayOf(
        clearRateAp.toDouble(), clearRateS.toDouble(), clearRateSs.toDouble(),
        clearRateSss.toDouble(), clearRateSssPlus.toDouble()
    )

    private fun blend(count: Int, total: Int, prior: Double): Int =
        ((count + PRIOR_WEIGHT * prior / SCALE) / (total + PRIOR_WEIGHT) * SCALE).toInt()

    /** Level -> average snapshot rates, used as the prior for charts the snapshot does not have. */
    private fun levelAverages(snapshot: List<Mai2NationalData>): Map<Int, DoubleArray> =
        snapshot.flatMap { it.nationalDataDetailList }.groupBy { it.level }.mapValues { (_, list) ->
            DoubleArray(5) { i -> list.map { it.priors()[i] }.average() }
        }

    /**
     * @param snapshot the bundled real national data
     * @param levelCounts musicId -> number of charts the music has (from the music metadata; may be empty)
     * @param bests (musicId, level) -> best score of every player that played the chart
     */
    fun build(
        snapshot: List<Mai2NationalData>,
        levelCounts: Map<Int, Int>,
        bests: Map<Pair<Int, Int>, List<Best>>,
        requestedLevels: Set<Int>
    ): List<Map<String, Any>> {
        val byId = snapshot.associateBy { it.musicId.toInt() }
        val averages = levelAverages(snapshot)

        // Charts that exist: everything in the snapshot, plus the charts the music metadata knows about.
        val charts = sortedMapOf<Int, MutableSet<Int>>()
        snapshot.forEach { e -> charts.getOrPut(e.musicId.toInt()) { sortedSetOf() }.addAll(e.nationalDataDetailList.map { it.level }) }
        levelCounts.forEach { (id, count) ->
            if (id >= UTAGE_MIN_ID) return@forEach
            charts.getOrPut(id) { sortedSetOf() }.addAll((0 until count).filter { it in DEFAULT_LEVELS })
        }

        return charts.mapNotNull { (id, levels) ->
            val details = levels.filter { it in requestedLevels }.mapNotNull { level ->
                val snap = byId[id]?.nationalDataDetailList?.firstOrNull { it.level == level }
                val prior = snap?.priors() ?: averages[level] ?: return@mapNotNull null
                val rows = bests[id to level].orEmpty()
                val total = rows.size
                val counts = intArrayOf(
                    rows.count { it.comboStatus >= 3 },
                    rows.count { it.achievement >= 970_000 },
                    rows.count { it.achievement >= 990_000 },
                    rows.count { it.achievement >= 1_000_000 },
                    rows.count { it.achievement >= 1_005_000 }
                )
                val r = IntArray(5) { blend(counts[it], total, prior[it]) }
                mapOf(
                    "level" to level,
                    "clearRateAp" to r[0], "clearRateS" to r[1], "clearRateSs" to r[2],
                    "clearRateSss" to r[3], "clearRateSssPlus" to r[4]
                )
            }
            if (details.isEmpty()) null else mapOf("musicId" to id.toLong(), "nationalDataDetailList" to details)
        }
    }
}
