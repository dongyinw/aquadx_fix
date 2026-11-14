package icu.samnyan.aqua.net.games

import com.querydsl.jpa.impl.JPAQueryFactory
import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.QAquaNetUser
import icu.samnyan.aqua.sega.chusan.model.userdata.QChu3UserData
import icu.samnyan.aqua.sega.general.model.QCard
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentDef
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService
import icu.samnyan.aqua.sega.maimai2.Mai2TournamentService.Companion.now
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import icu.samnyan.aqua.sega.chusan.model.userdata.QUserPlaylog as QChuPlaylog
import icu.samnyan.aqua.sega.ongeki.model.QUserData as QOngUserData
import icu.samnyan.aqua.sega.ongeki.model.QUserPlaylog as QOngPlaylog

/** A website tournament of CHUNITHM or O.N.G.E.K.I. (maimai DX tournaments live in [icu.samnyan.aqua.sega.maimai2.Mai2Tournament]). */
@Entity
@Table(name = "game_tournament")
class GameTournament(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var pk: Long = 0,
    var game: String = "",
    var tournamentId: Int = 0,
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
    var musicIds: String = "",
    var lockedMusicIds: String = "",
) {
    fun toDef() = Mai2TournamentDef().also {
        it.id = tournamentId; it.name = name; it.enabled = enabled; it.rankingKind = rankingKind; it.scoreType = scoreType
        it.noticeStartDate = noticeStartDate; it.noticeEndDate = noticeEndDate
        it.startDate = startDate; it.endDate = endDate; it.entryStartDate = entryStartDate; it.entryEndDate = entryEndDate
        it.musicIds = parse(musicIds); it.lockedMusicIds = parse(lockedMusicIds)
    }

    fun update(d: Mai2TournamentDef) = apply {
        tournamentId = d.id; name = d.name; enabled = d.enabled; rankingKind = d.rankingKind; scoreType = d.scoreType
        noticeStartDate = d.noticeStartDate; noticeEndDate = d.noticeEndDate
        startDate = d.startDate; endDate = d.endDate; entryStartDate = d.entryStartDate; entryEndDate = d.entryEndDate
        musicIds = d.musicIds.joinToString(","); lockedMusicIds = d.lockedMusicIds.joinToString(",")
    }

    private fun parse(s: String) = s.split(',').mapNotNull { it.trim().toIntOrNull() }
}

interface GameTournamentRepo : JpaRepository<GameTournament, Long> {
    fun findByGame(game: String): List<GameTournament>
    fun findByGameAndTournamentId(game: String, tournamentId: Int): GameTournament?
}

/**
 * Tournament rankings for CHUNITHM and O.N.G.E.K.I., scored like maimai DX: an entry is one credit that
 * contains every tournament song within the tournament dates, its score is the sum of the song scores minus the
 * same easy-difficulty penalty, and every player's best entry is ranked.
 *
 * Their playlogs have no credit id like maimai DX's loginDate, so plays of one player are cut into credits by
 * the track number restarting or a long pause between two plays.
 */
@Service
class GameTournamentService(
    private val repo: GameTournamentRepo,
    private val queryFactory: JPAQueryFactory,
) {
    /** One tournament-song play of any game, before it is cut into credits. */
    data class Raw(
        val userId: Long, val musicId: Int, val level: Int, val score: Int, val secondary: Int,
        val track: Int, val time: LocalDateTime,
    )

    companion object {
        val GAMES = setOf("chu3", "ongeki")
        /** Longest pause between two plays that can still belong to one credit. */
        const val CREDIT_GAP_MINUTES = 20L
        private const val CACHE_MILLIS = 60_000L
        private val log = LoggerFactory.getLogger(GameTournamentService::class.java)

        /** Cuts each player's plays into credits and converts them to the shared ranking input. */
        fun toPlays(rows: List<Raw>): List<Mai2TournamentService.Play> {
            var credit = 0L
            var prev: Raw? = null
            return rows.sortedWith(compareBy({ it.userId }, { it.time })).map { r ->
                val p = prev
                val newCredit = p == null || p.userId != r.userId ||
                    Duration.between(p.time, r.time).toMinutes() > CREDIT_GAP_MINUTES ||
                    (r.track > 0 && p.track > 0 && r.track <= p.track)
                if (newCredit) credit++
                prev = r
                Mai2TournamentService.Play(
                    r.userId, credit, r.musicId, r.level, r.score, r.secondary,
                    r.time.format(Mai2TournamentService.CONFIG_DATE_FMT),
                )
            }
        }

        fun parseTime(s: String): LocalDateTime? =
            runCatching { LocalDateTime.parse(s.take(19), Mai2TournamentService.CONFIG_DATE_FMT) }.getOrNull()
    }

    fun all(game: String) = repo.findByGame(game).map { it.toDef() }.sortedByDescending { it.id }

    fun find(game: String, id: Int) = repo.findByGameAndTournamentId(game, id)?.toDef()

    fun active(game: String, now: String = now()) = all(game).filter { it.isActive(now) }

    @Transactional
    fun save(game: String, def: Mai2TournamentDef) {
        val row = repo.findByGameAndTournamentId(game, def.id) ?: GameTournament(game = game)
        repo.save(row.update(def))
        cache.clear()
    }

    @Transactional
    fun delete(game: String, id: Int) {
        repo.delete(repo.findByGameAndTournamentId(game, id) ?: (404 - "Tournament not found"))
        cache.clear()
    }

    private val cache = ConcurrentHashMap<Int, Pair<Long, List<Mai2TournamentService.Entry>>>()

    fun ranking(game: String, t: Mai2TournamentDef): List<Mai2TournamentService.Entry> {
        val key = listOf(game, t.id, t.startDate, t.endDate, t.musicIds).hashCode()
        cache[key]?.let { (time, r) -> if (System.currentTimeMillis() - time < CACHE_MILLIS) return r }
        val raws = if (game == "chu3") loadChu3(t) else loadOngeki(t)
        return Mai2TournamentService.rank(toPlays(raws), t.musicIds).also { cache[key] = System.currentTimeMillis() to it }
    }

    data class Who(val name: String, val username: String?, val hidden: Boolean)

    fun leaderboard(game: String, t: Mai2TournamentDef): List<Mai2TournamentService.LeaderboardRow> {
        val ranked = ranking(game, t)
        if (ranked.isEmpty()) return emptyList()
        val who = if (game == "chu3") whoChu3(ranked.map { it.userId }) else whoOngeki(ranked.map { it.userId })
        return ranked.mapIndexedNotNull { i, e ->
            val w = who[e.userId] ?: return@mapIndexedNotNull null
            if (w.hidden) return@mapIndexedNotNull null
            Mai2TournamentService.LeaderboardRow(
                rank = i + 1, name = w.name, username = w.username,
                totalScore = e.totalScore, totalDxScore = e.totalDxScore, playDate = e.playDate, songs = e.songs,
            )
        }
    }

    private fun loadChu3(t: Mai2TournamentDef): List<Raw> {
        val p = QChuPlaylog.userPlaylog
        val u = QChu3UserData.chu3UserData
        val c = QCard.card
        var where = p.musicId.`in`(t.musicIds)
        parseTime(t.startDate)?.let { where = where.and(p.userPlayDate.goe(it)) }
        parseTime(t.endDate)?.let { where = where.and(p.userPlayDate.loe(it)) }
        return queryFactory
            .select(c.extId, p.musicId, p.level, p.score, p.maxCombo, p.track, p.userPlayDate)
            .from(p).innerJoin(p.user, u).innerJoin(u.card, c)
            .where(where)
            .fetch()
            .map { Raw(it.get(c.extId)!!, it.get(p.musicId)!!, it.get(p.level)!!, it.get(p.score)!!, it.get(p.maxCombo)!!, it.get(p.track)!!, it.get(p.userPlayDate)!!) }
    }

    private fun loadOngeki(t: Mai2TournamentDef): List<Raw> {
        val p = QOngPlaylog.userPlaylog
        val u = QOngUserData.userData
        val c = QCard.card
        var where = p.musicId.`in`(t.musicIds)
        if (t.startDate.isNotBlank()) where = where.and(p.userPlayDate.goe(t.startDate))
        if (t.endDate.isNotBlank()) where = where.and(p.userPlayDate.loe(t.endDate))
        return queryFactory
            .select(c.extId, p.musicId, p.level, p.techScore, p.platinumScore, p.sortNumber, p.userPlayDate)
            .from(p).innerJoin(p.user, u).innerJoin(u.card, c)
            .where(where)
            .fetch()
            .mapNotNull {
                val time = parseTime(it.get(p.userPlayDate)!!) ?: return@mapNotNull null
                Raw(it.get(c.extId)!!, it.get(p.musicId)!!, it.get(p.level)!!, it.get(p.techScore)!!, it.get(p.platinumScore)!!, it.get(p.sortNumber)!!, time)
            }
    }

    private fun whoChu3(ids: List<Long>): Map<Long, Who> {
        val u = QChu3UserData.chu3UserData
        val c = QCard.card
        val a = QAquaNetUser.aquaNetUser
        return queryFactory
            .select(c.extId, u.userName, c.rankingBanned, c.status, a.username, a.optOutOfLeaderboard)
            .from(u).innerJoin(u.card, c).leftJoin(c.aquaUser, a)
            .where(c.extId.`in`(ids)).fetch()
            .associate { it.get(c.extId)!! to Who(it.get(u.userName) ?: "", it.get(a.username), hidden(it.get(c.rankingBanned), it.get(a.optOutOfLeaderboard), it.get(c.status)?.isNormal)) }
    }

    private fun whoOngeki(ids: List<Long>): Map<Long, Who> {
        val u = QOngUserData.userData
        val c = QCard.card
        val a = QAquaNetUser.aquaNetUser
        return queryFactory
            .select(c.extId, u.userName, c.rankingBanned, c.status, a.username, a.optOutOfLeaderboard)
            .from(u).innerJoin(u.card, c).leftJoin(c.aquaUser, a)
            .where(c.extId.`in`(ids)).fetch()
            .associate { it.get(c.extId)!! to Who(it.get(u.userName) ?: "", it.get(a.username), hidden(it.get(c.rankingBanned), it.get(a.optOutOfLeaderboard), it.get(c.status)?.isNormal)) }
    }

    /** Players banned from rankings, opted out, or on abnormal cards are left off the list but keep their place. */
    private fun hidden(rankingBanned: Boolean?, optOut: Boolean?, normal: Boolean?) =
        rankingBanned == true || optOut == true || normal == false
}

/** Website tournament leaderboard and admin management for CHUNITHM and O.N.G.E.K.I.; same shape as the maimai DX API. */
@RestController
@API("/api/v2/game/{game}/tournament")
class GameTournamentApi(
    private val jwt: JWT,
    private val service: GameTournamentService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GameTournamentApi::class.java)
        private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
    }

    private fun game(g: Str): Str = if (g in GameTournamentService.GAMES) g else (404 - "Unknown game")

    private fun isAdmin(token: Str?) = token?.let { jwt.parse(it) }?.isAdmin == true

    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    private fun summary(t: Mai2TournamentDef, now: String) = mapOf(
        "id" to t.id, "name" to t.name, "enabled" to t.enabled, "active" to t.isActive(now), "expired" to t.isExpired(now),
        "rankingKind" to t.rankingKind, "scoreType" to t.scoreType,
        "noticeStartDate" to t.noticeStartDate, "noticeEndDate" to t.noticeEndDate,
        "startDate" to t.startDate, "endDate" to t.endDate,
        "entryStartDate" to t.entryStartDate, "entryEndDate" to t.entryEndDate,
        "musicIds" to t.musicIds, "lockedMusicIds" to t.lockedMusicIds,
    )

    @API("list")
    @Doc("Tournaments of this game that are currently running (enabled and not expired)")
    fun list(@PathVariable game: Str): List<Map<String, Any>> {
        val now = now()
        return service.active(game(game), now).map { summary(it, now) }
    }

    @API("ranking")
    @Doc("Leaderboard of a tournament. Disabled tournaments are only visible to admins.")
    fun ranking(@PathVariable game: Str, @RP id: Int, @RP token: Str?): Map<String, Any> {
        val g = game(game)
        val t = service.find(g, id)?.takeIf { it.enabled || isAdmin(token) } ?: (404 - "Tournament not found")
        return mapOf("tournament" to summary(t, now()), "ranking" to service.leaderboard(g, t))
    }

    @API("admin/list")
    @Doc("All tournaments of this game, including disabled and expired ones")
    fun adminList(@PathVariable game: Str, @RP token: Str): List<Map<String, Any>> {
        admin(token)
        val now = now()
        return service.all(game(game)).map { summary(it, now) }
    }

    @API(value = ["admin/save"], method = [RequestMethod.POST])
    @Doc("Create or update a tournament")
    fun adminSave(@PathVariable game: Str, @RP token: Str, @RB body: Mai2TournamentDef): Map<String, Any> {
        val g = game(game)
        val actor = admin(token)
        if (body.id <= 0) 400 - "大赛 ID 必须为正整数"
        body.name = body.name.trim()
        if (body.name.isEmpty()) 400 - "请填写大赛名称"
        body.musicIds = body.musicIds.distinct()
        if (body.musicIds.isEmpty()) 400 - "请至少填写一首课题曲"
        if (body.musicIds.any { it <= 0 }) 400 - "曲目 ID 必须为正整数"
        body.lockedMusicIds = body.lockedMusicIds.distinct()
        if (!body.musicIds.containsAll(body.lockedMusicIds)) 400 - "锁定曲目必须是课题曲之一"

        listOf(body::noticeStartDate, body::noticeEndDate, body::startDate, body::endDate, body::entryStartDate, body::entryEndDate)
            .forEach { f ->
                val v = f.get().trim().also { f.set(it) }
                if (v.isNotEmpty() && !dateRegex.matches(v)) 400 - "日期格式应为 yyyy-MM-dd HH:mm:ss：$v"
            }
        if (body.startDate.isEmpty() || body.endDate.isEmpty()) 400 - "请填写开始和结束时间"
        if (body.startDate > body.endDate) 400 - "开始时间不能晚于结束时间"

        service.save(g, body)
        log.info("Admin {} saved {} tournament {}", actor.auId, g, body.id)
        return summary(body, now())
    }

    @API("admin/delete")
    @Doc("Delete a tournament. Playlogs are not affected.")
    fun adminDelete(@PathVariable game: Str, @RP token: Str, @RP id: Int): Map<String, Any> {
        val g = game(game)
        val actor = admin(token)
        service.delete(g, id)
        log.info("Admin {} deleted {} tournament {}", actor.auId, g, id)
        return mapOf("success" to true)
    }
}
