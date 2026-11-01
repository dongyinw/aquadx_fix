package icu.samnyan.aqua.sega.maimai2

import com.querydsl.jpa.impl.JPAQueryFactory
import ext.jstNow
import icu.samnyan.aqua.net.db.QAquaNetUser
import icu.samnyan.aqua.sega.general.model.QCard
import icu.samnyan.aqua.sega.maimai2.model.userdata.QMai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.QMai2UserPlaylog
import jakarta.annotation.PostConstruct
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Legacy tournament definitions from application.properties, e.g.
 *
 * game.maimai2.tournaments[0].id=20006
 * game.maimai2.tournaments[0].music-ids=11810,11745,12025
 *
 * Tournaments are managed in the web admin console now. These entries are only imported into the database
 * when the tournament table is empty.
 */
@Configuration
@ConfigurationProperties(prefix = "game.maimai2")
class Mai2TournamentProps {
    var tournaments: MutableList<Mai2TournamentDef> = mutableListOf()
}

class Mai2TournamentDef {
    var id = 0
    var name = ""
    var enabled = true
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

    /** [now] is formatted as yyyy-MM-dd HH:mm:ss, so plain string comparison orders the dates. */
    fun isExpired(now: String) = noticeEndDate.ifBlank { endDate }.let { it.isNotBlank() && now > it }

    /** Shown to cabinets and on the website: enabled and not expired. */
    fun isActive(now: String) = enabled && id != 0 && musicIds.isNotEmpty() && !isExpired(now)

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

    fun toEntity() = Mai2Tournament(
        id = id, name = name, enabled = enabled, rankingKind = rankingKind, scoreType = scoreType,
        noticeStartDate = noticeStartDate, noticeEndDate = noticeEndDate,
        startDate = startDate, endDate = endDate, entryStartDate = entryStartDate, entryEndDate = entryEndDate,
        musicIds = musicIds.joinToString(","), lockedMusicIds = lockedMusicIds.joinToString(","),
    )
}

@Entity
@Table(name = "maimai2_tournament")
class Mai2Tournament(
    @Id var id: Int = 0,
    var name: String = "",
    var enabled: Boolean = true,
    var rankingKind: Int = 0,
    var scoreType: Int = 0,
    var noticeStartDate: String = "",
    var noticeEndDate: String = "",
    var startDate: String = "",
    var endDate: String = "",
    var entryStartDate: String = "",
    var entryEndDate: String = "",
    /** Comma separated music ids */
    var musicIds: String = "",
    var lockedMusicIds: String = "",
) {
    fun toDef() = Mai2TournamentDef().also {
        it.id = id; it.name = name; it.enabled = enabled; it.rankingKind = rankingKind; it.scoreType = scoreType
        it.noticeStartDate = noticeStartDate; it.noticeEndDate = noticeEndDate
        it.startDate = startDate; it.endDate = endDate; it.entryStartDate = entryStartDate; it.entryEndDate = entryEndDate
        it.musicIds = parseIds(musicIds); it.lockedMusicIds = parseIds(lockedMusicIds)
    }

    companion object {
        fun parseIds(s: String) = s.split(',').mapNotNull { it.trim().toIntOrNull() }
    }
}

interface Mai2TournamentRepo : JpaRepository<Mai2Tournament, Int>

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
    val repo: Mai2TournamentRepo,
    private val queryFactory: JPAQueryFactory,
) {
    data class Song(val musicId: Int, val level: Int, val achievement: Int, val deluxscore: Int)
    data class Entry(
        val userId: Long, val totalScore: Long, val totalDxScore: Long, val playDate: String,
        val songs: List<Song> = emptyList(),
    )

    data class LeaderboardRow(
        val rank: Int, val name: String, val username: String?, val totalScore: Long, val totalDxScore: Long,
        val playDate: String, val songs: List<Song>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(Mai2TournamentService::class.java)
        private val RANKING_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val CONFIG_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val CACHE_MILLIS = 60_000L

        fun now(): String = LocalDateTime.now().format(CONFIG_DATE_FMT)

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
                    songs = best.map { Song(it.musicId, it.level, it.achievement, it.deluxscore) },
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

    /** One-time import of tournaments configured in application.properties. */
    @PostConstruct
    fun importFromProperties() {
        val defs = props.tournaments.filter { it.id != 0 }
        if (defs.isEmpty() || repo.count() > 0) return
        repo.saveAll(defs.map { it.toEntity() })
        log.info("Imported {} maimai2 tournament(s) from application.properties", defs.size)
    }

    fun all(): List<Mai2TournamentDef> = repo.findAll().map { it.toDef() }.sortedByDescending { it.id }

    fun active(now: String = now()) = all().filter { it.isActive(now) }

    fun find(id: Int) = repo.findById(id).orElse(null)?.toDef()

    fun gameTournamentInfo(now: String = now()) = active(now).map { it.toGameInfo() }

    // Rankings are cached briefly so the website and many cabinet logins don't rescan playlogs every time.
    private val cache = ConcurrentHashMap<Int, Pair<Long, List<Entry>>>()

    fun ranking(t: Mai2TournamentDef): List<Entry> {
        val key = t.hashKey()
        cache[key]?.let { (time, r) -> if (System.currentTimeMillis() - time < CACHE_MILLIS) return r }
        return rank(loadPlays(t), t.musicIds).also { cache[key] = System.currentTimeMillis() to it }
    }

    /** Different settings of the same tournament (e.g. edited dates) must not share a cache entry. */
    private fun Mai2TournamentDef.hashKey() = listOf(id, startDate, endDate, musicIds).hashCode()

    fun clearCache() = cache.clear()

    /** Response body for GetUserScoreRankingApi. tournamentId = 0 means the user has no entry. */
    fun userScoreRanking(userId: Long, competitionId: Int): Map<String, Any> {
        val none = mapOf("tournamentId" to 0, "totalScore" to 0L, "ranking" to 0, "rankingDate" to "")
        val t = find(competitionId)?.takeIf { it.enabled && it.musicIds.isNotEmpty() } ?: return none
        val ranked = ranking(t)
        val idx = ranked.indexOfFirst { it.userId == userId }
        if (idx < 0) return none
        return mapOf(
            "tournamentId" to t.id,
            "totalScore" to ranked[idx].totalScore,
            "ranking" to idx + 1,
            "rankingDate" to jstNow().format(RANKING_DATE_FMT)
        )
    }

    /**
     * Website leaderboard. Ranks match the in-game ranking; players hidden from leaderboards
     * (ranking banned, opted out, or deleted cards) are left out of the list but keep their place.
     */
    fun leaderboard(t: Mai2TournamentDef): List<LeaderboardRow> {
        val ranked = ranking(t)
        if (ranked.isEmpty()) return emptyList()
        val u = QMai2UserDetail.mai2UserDetail
        val c = QCard.card
        val a = QAquaNetUser.aquaNetUser
        val info = queryFactory
            .select(c.extId, u.userName, c.rankingBanned, c.status, a.username, a.optOutOfLeaderboard)
            .from(u).innerJoin(u.card, c).leftJoin(c.aquaUser, a)
            .where(c.extId.`in`(ranked.map { it.userId }))
            .fetch()
            .associateBy { it.get(c.extId)!! }

        return ranked.mapIndexedNotNull { i, e ->
            val row = info[e.userId] ?: return@mapIndexedNotNull null
            val hidden = row.get(c.rankingBanned) == true || row.get(a.optOutOfLeaderboard) == true ||
                row.get(c.status)?.isNormal == false
            if (hidden) return@mapIndexedNotNull null
            LeaderboardRow(
                rank = i + 1, name = row.get(u.userName) ?: "", username = row.get(a.username),
                totalScore = e.totalScore, totalDxScore = e.totalDxScore, playDate = e.playDate, songs = e.songs,
            )
        }
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
