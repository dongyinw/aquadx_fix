package icu.samnyan.aqua.sega.maimai2

import com.querydsl.jpa.impl.JPAQueryFactory
import ext.jstNow
import icu.samnyan.aqua.sega.general.model.QCard
import icu.samnyan.aqua.sega.maimai2.model.userdata.QMai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.QMai2UserPlaylog
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/**
 * Tournament (大会 / KING of Performai style) definitions, e.g.
 *
 * game.maimai2.tournaments[0].id=20006
 * game.maimai2.tournaments[0].music-ids=11810,11745,12025
 * game.maimai2.tournaments[0].start-date=2026-10-29 07:00:00
 */
@Configuration
@ConfigurationProperties(prefix = "game.maimai2")
class Mai2TournamentProps {
    var tournaments: MutableList<Mai2TournamentDef> = mutableListOf()
}

class Mai2TournamentDef {
    var id = 0
    var name = ""
    var rankingKind = 0
    var scoreType = 0
    var noticeStartDate = ""
    var noticeEndDate = ""
    var startDate = ""
    var endDate = ""
    var entryStartDate = ""
    var entryEndDate = ""
    var musicIds: List<Int> = emptyList()
    var lockedMusicIds: List<Int> = emptyList()

    fun toGameInfo() = mapOf(
        "tournamentId" to id,
        "tournamentName" to name,
        "rankingKind" to rankingKind,
        "scoreType" to scoreType,
        "noticeStartDate" to noticeStartDate.ifBlank { startDate },
        "noticeEndDate" to noticeEndDate.ifBlank { endDate },
        "startDate" to startDate,
        "endDate" to endDate,
        "entryStartDate" to entryStartDate.ifBlank { startDate },
        "entryEndDate" to entryEndDate.ifBlank { endDate },
        "gameTournamentMusicList" to musicIds.map { mapOf(
            "tournamentId" to id,
            "musicId" to it,
            "level" to 0,
            "isFirstLock" to (it in lockedMusicIds)
        ) }
    )
}

/**
 * Computes tournament score rankings from uploaded playlogs.
 *
 * A tournament entry is one credit (playlogs sharing the same loginDate) that contains every tournament song
 * within [startDate, endDate]. Its total score is the sum of achievements, minus a difficulty penalty
 * (BASIC -3%, ADVANCED -2%, EXPERT -1%, MASTER/Re:MASTER none). Each player's best entry is ranked by
 * total score, then total DX score, then the earliest play.
 */
@Service
class Mai2TournamentService(
    val props: Mai2TournamentProps,
    private val queryFactory: JPAQueryFactory,
) {
    data class Entry(val userId: Long, val totalScore: Long, val totalDxScore: Long, val playDate: String)

    companion object {
        private val RANKING_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        fun levelPenalty(level: Int) = when (level) {
            0 -> 30000
            1 -> 20000
            2 -> 10000
            else -> 0
        }

        /** Returns each player's best entry, best first. */
        fun rank(plays: List<Play>, musicIds: List<Int>): List<Entry> = plays
            .groupBy { it.userId to it.loginDate }
            .mapNotNull { (key, credit) ->
                val best = musicIds.map { mid ->
                    credit.filter { it.musicId == mid }
                        .maxWithOrNull(compareBy({ it.achievement - levelPenalty(it.level) }, { it.deluxscore }))
                        ?: return@mapNotNull null
                }
                Entry(
                    userId = key.first,
                    totalScore = best.sumOf { (it.achievement - levelPenalty(it.level)).toLong() },
                    totalDxScore = best.sumOf { it.deluxscore.toLong() },
                    playDate = best.maxOf { it.userPlayDate },
                )
            }
            .groupBy { it.userId }
            .map { (_, entries) -> entries.sortedWith(entryOrder).first() }
            .sortedWith(entryOrder)

        private val entryOrder = compareByDescending<Entry> { it.totalScore }
            .thenByDescending { it.totalDxScore }
            .thenBy { it.playDate }
    }

    data class Play(
        val userId: Long, val loginDate: Long, val musicId: Int, val level: Int,
        val achievement: Int, val deluxscore: Int, val userPlayDate: String,
    )

    fun gameTournamentInfo() = props.tournaments.filter { it.id != 0 }.map { it.toGameInfo() }

    /** Response body for GetUserScoreRankingApi. tournamentId = 0 means the user has no entry. */
    fun userScoreRanking(userId: Long, competitionId: Int): Map<String, Any> {
        val none = mapOf("tournamentId" to 0, "totalScore" to 0L, "ranking" to 0, "rankingDate" to "")
        val t = props.tournaments.find { it.id == competitionId && it.musicIds.isNotEmpty() } ?: return none
        val ranked = rank(loadPlays(t), t.musicIds)
        val idx = ranked.indexOfFirst { it.userId == userId }
        if (idx < 0) return none
        return mapOf(
            "tournamentId" to t.id,
            "totalScore" to ranked[idx].totalScore,
            "ranking" to idx + 1,
            "rankingDate" to jstNow().format(RANKING_DATE_FMT)
        )
    }

    private fun loadPlays(t: Mai2TournamentDef): List<Play> {
        val p = QMai2UserPlaylog.mai2UserPlaylog
        val u = QMai2UserDetail.mai2UserDetail
        val c = QCard.card
        var where = p.musicId.`in`(t.musicIds)
        if (t.startDate.isNotBlank()) where = where.and(p.userPlayDate.goe(t.startDate))
        if (t.endDate.isNotBlank()) where = where.and(p.userPlayDate.loe(t.endDate))

        return queryFactory
            .select(c.extId, p.loginDate, p.musicId, p.level, p.achievement, p.deluxscore, p.userPlayDate)
            .from(p).innerJoin(p.user, u).innerJoin(u.card, c)
            .where(where)
            .fetch()
            .map { Play(
                it.get(c.extId)!!, it.get(p.loginDate)!!, it.get(p.musicId)!!, it.get(p.level)!!,
                it.get(p.achievement)!!, it.get(p.deluxscore)!!, it.get(p.userPlayDate)!!,
            ) }
    }
}
