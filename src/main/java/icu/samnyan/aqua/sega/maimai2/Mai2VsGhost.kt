package icu.samnyan.aqua.sega.maimai2

import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserFriendSeasonRanking
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhost
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhostEntry
import kotlin.math.abs

/**
 * Friend battle ("友人对战") needs other players' ghosts. A cabinet uploads the ghost of its latest play in
 * UpsertUserAll.userGhost and downloads up to 15 ghosts of other players with GetUserGhost; whatever is
 * missing is filled with NPC ghosts by the client, so without real data every opponent is an NPC.
 */
object Mai2VsGhost {
    const val MAX_RETURNED = 15
    const val MAX_STORED_PER_USER = 30

    fun encodeBits(bits: List<Byte>?) = bits.orEmpty().joinToString(",") { (it.toInt() and 0xFF).toString() }

    fun decodeBits(text: String): List<Int> =
        if (text.isBlank()) emptyList() else text.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** Null when the upload carries nothing usable (the client only sends a ghost after a finished play). */
    fun toEntry(user: Mai2UserDetail, up: Mai2UserGhost, existing: Mai2UserGhostEntry?): Mai2UserGhostEntry? {
        if (up.musicId <= 0 || up.resultBitList.isNullOrEmpty()) return null
        return (existing ?: Mai2UserGhostEntry()).also {
            it.user = user
            it.musicId = up.musicId
            it.difficulty = up.difficulty
            it.name = up.name
            it.iconId = up.iconId
            it.plateId = up.plateId
            it.titleId = up.titleId
            it.rate = up.rate
            it.udemaeRate = up.udemaeRate
            it.courseRank = up.courseRank
            it.classRank = up.classRank
            it.classValue = up.classValue
            it.playDatetime = up.playDatetime
            it.shopId = up.shopId
            it.regionCode = up.regionCode
            it.typeId = up.typeId
            it.version = up.version
            it.resultBitList = encodeBits(up.resultBitList)
            it.resultNum = up.resultNum
            it.achievement = up.achievement
        }
    }

    /**
     * Picks the ghosts to send: one per player first, the players closest in rating to [myRating] first,
     * then more songs from those same players until [limit]. [recent] must be newest first.
     */
    fun select(recent: List<Mai2UserGhostEntry>, myRating: Int, limit: Int = MAX_RETURNED): List<Mai2UserGhostEntry> {
        val byUser = recent.groupBy { it.user.id }.values
            .sortedBy { abs(it.first().rate - myRating) }
        val firsts = byUser.map { it.first() }
        val picked = firsts.take(limit).toMutableList()
        if (picked.size < limit) {
            picked += byUser.flatMap { it.drop(1) }.sortedBy { abs(it.rate - myRating) }.take(limit - picked.size)
        }
        return picked
    }

    fun toResponse(g: Mai2UserGhostEntry): Map<String, Any?> = mapOf(
        "name" to g.name,
        "iconId" to g.iconId,
        "plateId" to g.plateId,
        "titleId" to g.titleId,
        "rate" to g.rate,
        "udemaeRate" to g.udemaeRate,
        "courseRank" to g.courseRank,
        "classRank" to g.classRank,
        "classValue" to g.classValue,
        "playDatetime" to g.playDatetime,
        "shopId" to g.shopId,
        "regionCode" to g.regionCode,
        "typeId" to g.typeId,
        "musicId" to g.musicId,
        "difficulty" to g.difficulty,
        "version" to g.version,
        "resultBitList" to decodeBits(g.resultBitList),
        "resultNum" to g.resultNum,
        "achievement" to g.achievement,
    )
}

/**
 * The friend season ranking reward (top plate etc.) is decided by the rank stored in the player's season row.
 * Cabinets only upload points; the rank has to be computed here, otherwise the rank reward is never granted.
 */
object Mai2FriendSeason {
    fun withRank(row: Mai2UserFriendSeasonRanking, countAhead: (Mai2UserFriendSeasonRanking) -> Long): Map<String, Any?> = mapOf(
        "seasonId" to row.seasonId,
        "point" to row.point,
        "rank" to (if (row.point > 0) countAhead(row).toInt() + 1 else 0),
        "rewardGet" to row.rewardGet,
        "userName" to row.userName,
        "recordDate" to row.recordDate,
    )
}
