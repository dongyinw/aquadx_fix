package icu.samnyan.aqua.sega.maimai2

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGhostEntry
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.concurrent.thread

/**
 * Friend battle is only fun with enough opponents, and ghosts are only recorded when someone plays after the
 * server started keeping them. This turns players' ordinary recent plays into ghosts too.
 *
 * Those ghosts carry the player's name, icon, rating and the real score of the play but no note-by-note record
 * (the playlog has none). The cabinet then plays them like an NPC that finishes with exactly that score, which is
 * a case it handles. A real recording uploaded later for the same song replaces them, and they are never written
 * over one. Players who are banned, hidden from leaderboards or opted out of them are left out.
 */
object Mai2GhostSeed {
    data class Play(val musicId: Int, val level: Int, val achievement: Int, val playDate: String, val placeId: Int)

    /** The newest play of each song and difficulty, newest first, at most [limit] of them. [plays] must be newest first. */
    fun pick(plays: List<Play>, limit: Int): List<Play> =
        plays.filter { it.musicId in 1 until 100000 && it.level in 0..4 && it.achievement > 0 }
            .distinctBy { it.musicId to it.level }
            .take(limit)

    /** Null when [existing] is a real recording, which must not be replaced by a score-only ghost. */
    fun build(profile: Mai2UserDetail, classValue: Int, udemaeRate: Int, play: Play, existing: Mai2UserGhostEntry?): Mai2UserGhostEntry? {
        if (existing != null && existing.resultNum > 0) return null
        return (existing ?: Mai2UserGhostEntry()).also {
            it.user = profile
            it.musicId = play.musicId
            it.difficulty = play.level
            it.name = profile.userName
            it.iconId = profile.iconId
            it.plateId = profile.plateId
            it.titleId = profile.titleId
            it.rate = profile.playerRating
            it.udemaeRate = udemaeRate
            it.courseRank = profile.courseRank
            it.classRank = profile.classRank
            it.classValue = classValue
            it.playDatetime = play.playDate
            it.shopId = play.placeId
            it.regionCode = profile.lastRegionId
            it.typeId = play.level
            // The cabinet only shows ghosts of a data version it knows; 0 is accepted by every version.
            it.version = 0
            it.resultBitList = ""
            it.resultNum = 0
            it.achievement = play.achievement
        }
    }
}

@Service
class Mai2GhostSeeder(
    private val repos: Mai2Repos,
    txManager: PlatformTransactionManager,
    @Value("\${game.maimai2.ghost-seed-enabled:true}") val enabled: Boolean,
    @Value("\${game.maimai2.ghost-seed-days:60}") val days: Int,
    @Value("\${game.maimai2.ghost-seed-per-player:15}") val perPlayer: Int,
) {
    @PersistenceContext
    private lateinit var em: EntityManager

    private val tx = TransactionTemplate(txManager)
    private val log = LoggerFactory.getLogger(Mai2GhostSeeder::class.java)

    data class Result(val finishedAt: String, val players: Int, val written: Int, val removed: Int)

    @Volatile private var lastResult: Result? = null
    @Volatile private var running = false

    fun status() = mapOf(
        "enabled" to enabled,
        "days" to days,
        "perPlayer" to perPlayer,
        "total" to repos.userGhost.count(),
        "recorded" to repos.userGhost.countByResultNumGreaterThan(0),
        "fromPlays" to repos.userGhost.countByResultNum(0),
        "players" to repos.userGhost.countPlayers(),
        "running" to running,
        "last" to lastResult,
    )

    @Scheduled(cron = "\${game.maimai2.ghost-seed-cron:0 30 4 * * *}", zone = "\${game.maimai2.ghost-seed-zone:Asia/Shanghai}")
    fun scheduled() { if (enabled) runSeed() }

    /** A fresh server has no ghosts at all, so do not make players wait for the first scheduled run. */
    @EventListener(ApplicationReadyEvent::class)
    fun onStart() {
        if (enabled && repos.userGhost.count() < 50) thread(name = "ghost-seed") { runSeed() }
    }

    /** Runs one pass unless one is already running. */
    @Synchronized
    fun runSeed(): Result? {
        if (running) return null
        running = true
        return try {
            tx.execute { seed() }?.also { lastResult = it; log.info("Ghost seed: {} players, {} ghosts written, {} removed", it.players, it.written, it.removed) }
        } catch (e: Exception) {
            log.error("Ghost seed failed", e)
            null
        } finally {
            running = false
        }
    }

    private fun seed(): Result {
        val since = LocalDate.now().minusDays(days.toLong()).toString()
        val eligible = em.createQuery(
            """select u from Maimai2UserData u join fetch u.card c left join fetch c.aquaUser a
               where u.banState = 0 and u.lastPlayDate >= :since and c.rankingBanned = false
               and (a is null or a.optOutOfLeaderboard = false)""", Mai2UserDetail::class.java)
            .setParameter("since", since).resultList
            .filter { it.card?.status?.isNormal == true }
        val eligibleIds = eligible.map { it.id }.toSet()

        var written = 0
        for (profile in eligible) {
            val plays = em.createQuery(
                """select p.musicId, p.level, p.achievement, p.userPlayDate, p.placeId from Mai2UserPlaylog p
                   where p.user.id = :uid and p.userPlayDate >= :since and p.isPlayTutorial = false
                   order by p.userPlayDate desc""", Array<Any>::class.java)
                .setParameter("uid", profile.id).setParameter("since", since).setMaxResults(300).resultList
                .map { Mai2GhostSeed.Play((it[0] as Number).toInt(), (it[1] as Number).toInt(), (it[2] as Number).toInt(), it[3].toString(), (it[4] as Number).toInt()) }
            val picked = Mai2GhostSeed.pick(plays, perPlayer)
            if (picked.isEmpty()) continue

            val udemae = repos.userUdemae.findSingleByUser_Card_ExtId(profile.card!!.extId)
            for (play in picked) {
                val entry = Mai2GhostSeed.build(
                    profile, udemae?.classValue ?: 0, udemae?.rate ?: 0, play,
                    repos.userGhost.findByUserAndMusicIdAndDifficulty(profile, play.musicId, play.level),
                ) ?: continue
                repos.userGhost.save(entry)
                written++
            }
            // Keep each player to the same number of ghosts as the uploads do.
            repos.userGhost.deleteAll(repos.userGhost.findByUserOrderByPlayDatetimeDescIdDesc(profile).drop(Mai2VsGhost.MAX_STORED_PER_USER))
        }

        // Players who became ineligible (banned, hidden, opted out) must not keep appearing as opponents.
        val stale = repos.userGhost.findAll().filter { it.resultNum == 0 && it.user.id !in eligibleIds }
        repos.userGhost.deleteAll(stale)
        return Result(LocalDateTime.now().toString().replace('T', ' ').take(19), eligible.size, written, stale.size)
    }
}

@RestController
@API("/api/v2/admin/mai2-ghost")
class Mai2GhostAdminApi(private val jwt: JWT, private val seeder: Mai2GhostSeeder) {
    private fun admin(token: Str) = jwt.auth(token).also { if (!it.isAdmin) 403 - "Admin permission required" }

    @API("/status")
    @Doc("Size of the friend battle ghost pool and the result of the last refresh.", "Ghost pool")
    fun status(@RP token: Str) = admin(token).let { seeder.status() }

    @API("/seed")
    @Doc("Refresh the ghost pool from players' recent plays now.", "Ghost pool")
    fun seed(@RP token: Str): Map<String, Any?> {
        admin(token)
        if (seeder.runSeed() == null && !seeder.status()["running"].let { it == true }) 500 - "补充失败，请查看服务器日志"
        return seeder.status()
    }
}
