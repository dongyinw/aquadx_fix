package icu.samnyan.aqua.sega.maimai2

import ext.jstNow
import ext.minus
import icu.samnyan.aqua.net.Feature
import icu.samnyan.aqua.net.FeatureSwitches
import icu.samnyan.aqua.net.games.GenericMusicMeta
import icu.samnyan.aqua.net.games.mai2.Maimai2
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserClaimLog
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserMusicDetail
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * "创哥理赔": self-service tools for when the server was unavailable and a play was lost.
 * Players can re-submit the best result of a song and edit their MaiMile. Both only ever touch the player's own
 * profile, never create playlogs (so tournaments are unaffected), and every use is written to an audit log that
 * admins can review. Abuse is meant to be handled by banning the account.
 */
object Mai2Claim {
    const val KIND_SCORE = "score"
    const val KIND_MILE = "mile"
    const val MAX_MILE = 99999
    const val MAX_ACHIEVEMENT = 1010000
    const val MAX_DX_SCORE = 9999
    const val MAX_STATUS = 4

    /** Achievement (x10000) needed for each rank id, highest first: SSS+ 100.5% ... C 50%. */
    private val RANK_THRESHOLDS = listOf(
        1005000 to 13, 1000000 to 12, 995000 to 11, 990000 to 10, 980000 to 9, 970000 to 8, 940000 to 7,
        900000 to 6, 800000 to 5, 750000 to 4, 700000 to 3, 600000 to 2, 500000 to 1,
    )

    fun scoreRank(achievement: Int) = RANK_THRESHOLDS.firstOrNull { achievement >= it.first }?.second ?: 0

    data class ScoreInput(
        val musicId: Int,
        val level: Int,
        val achievement: Int,
        val deluxscore: Int,
        val comboStatus: Int,
        val syncStatus: Int,
    )

    /** The reason a submission is rejected, or null when it is acceptable. */
    fun validate(meta: GenericMusicMeta?, i: ScoreInput): String? = when {
        meta == null || i.musicId !in 1 until 100000 -> "找不到这首歌"
        i.level !in 0..4 || meta.notes.getOrNull(i.level)?.lv == null -> "这首歌没有所选难度"
        i.achievement !in 0..MAX_ACHIEVEMENT -> "达成率必须在 0% 到 101% 之间"
        i.deluxscore !in 0..MAX_DX_SCORE -> "DX 分数不合理"
        i.comboStatus !in 0..MAX_STATUS || i.syncStatus !in 0..MAX_STATUS -> "FC/AP 或 FS/FDX 状态不合法"
        i.comboStatus >= 3 && i.achievement < 1000000 -> "AP 成绩的达成率不可能低于 100%"
        i.comboStatus == 4 && i.achievement != MAX_ACHIEVEMENT -> "AP+ 成绩的达成率必须是 101%"
        else -> null
    }

    /**
     * Sets the song's best-score row to the submitted result, whether that is higher or lower than what is stored,
     * so a wrong entry can be corrected too. Returns null when the row already has exactly these values.
     * A song without a row gets one and counts as one play; an existing row keeps its play count.
     * The row is changed in place, so read any old values before calling this.
     */
    fun merge(user: Mai2UserDetail, existing: Mai2UserMusicDetail?, i: ScoreInput): Mai2UserMusicDetail? {
        if (existing != null && i.achievement == existing.achievement && i.deluxscore == existing.deluxscoreMax &&
            i.comboStatus == existing.comboStatus && i.syncStatus == existing.syncStatus) return null
        return (existing ?: Mai2UserMusicDetail().also {
            it.user = user; it.musicId = i.musicId; it.level = i.level; it.playCount = 1
        }).also {
            it.achievement = i.achievement
            it.deluxscoreMax = i.deluxscore
            it.comboStatus = i.comboStatus
            it.syncStatus = i.syncStatus
            it.scoreRank = scoreRank(i.achievement)
        }
    }
}

@Service
class Mai2ClaimService(
    val repos: Mai2Repos,
    val maimai2: Maimai2,
    val features: FeatureSwitches,
    @Value("\${game.maimai2.claim.score-daily-limit:20}") val scoreDailyLimit: Int,
    @Value("\${game.maimai2.claim.mile-daily-limit:3}") val mileDailyLimit: Int,
) {
    private fun startOfToday(): LocalDateTime = jstNow().toLocalDate().atStartOfDay()

    private fun used(user: Mai2UserDetail, kind: String) =
        repos.userClaimLog.countByUserAndKindAndCreatedAtGreaterThanEqual(user, kind, startOfToday()).toInt()

    private fun requireUsable(user: Mai2UserDetail) {
        features.require(Feature.CLAIM)
        if (user.banState != 0) 403 - "账号已被限制，无法使用该功能"
    }

    fun status(user: Mai2UserDetail) = mapOf(
        "hasProfile" to true,
        "point" to user.point,
        "totalPoint" to user.totalPoint,
        "scoreUsed" to used(user, Mai2Claim.KIND_SCORE),
        "scoreLimit" to scoreDailyLimit,
        "mileUsed" to used(user, Mai2Claim.KIND_MILE),
        "mileLimit" to mileDailyLimit,
    )

    private fun log(user: Mai2UserDetail, kind: String, musicId: Int, level: Int, old: Int, new: Int, detail: String) {
        repos.userClaimLog.save(Mai2UserClaimLog().also {
            it.user = user
            it.kind = kind
            it.musicId = musicId
            it.level = level
            it.oldValue = old
            it.newValue = new
            it.detail = detail.take(255)
            it.createdAt = jstNow()
        })
    }

    fun claimMile(user: Mai2UserDetail, point: Int): Map<String, Any?> {
        requireUsable(user)
        if (point !in 0..Mai2Claim.MAX_MILE) 400 - "舞里程必须在 0 到 ${Mai2Claim.MAX_MILE} 之间"
        if (point == user.point) 400 - "舞里程没有变化"
        if (used(user, Mai2Claim.KIND_MILE) >= mileDailyLimit) 429 - "今天修改舞里程的次数已用完（每天 $mileDailyLimit 次）"

        val old = user.point
        user.point = point
        // The lifetime total can never be lower than what is currently held.
        user.totalPoint = maxOf(user.totalPoint, point)
        repos.userData.save(user)
        log(user, Mai2Claim.KIND_MILE, 0, 0, old, point, "舞里程 $old -> $point")
        return status(user)
    }

    fun claimScore(user: Mai2UserDetail, input: Mai2Claim.ScoreInput): Map<String, Any?> {
        requireUsable(user)
        Mai2Claim.validate(maimai2.musicMapping[input.musicId], input)?.let { 400 - it }

        val existing = repos.userMusicDetail.findByUserAndMusicIdAndLevel(user, input.musicId, input.level)
        // merge() edits the stored row in place, so keep what it held for the audit log first.
        val before = existing?.let { "${it.achievement} DX ${it.deluxscoreMax} combo ${it.comboStatus} sync ${it.syncStatus}" } ?: "无记录"
        val oldAchievement = existing?.achievement ?: 0
        val merged = Mai2Claim.merge(user, existing, input) ?: (400 - "这首歌的成绩已经和你填写的完全一致，无需修改")
        if (used(user, Mai2Claim.KIND_SCORE) >= scoreDailyLimit) 429 - "今天补传成绩的次数已用完（每天 $scoreDailyLimit 次）"

        repos.userMusicDetail.save(merged)
        val name = maimai2.musicMapping[input.musicId]?.name ?: input.musicId.toString()
        log(user, Mai2Claim.KIND_SCORE, input.musicId, input.level, oldAchievement, merged.achievement,
            "$name Lv${input.level} 达成率 ${input.achievement} DX ${input.deluxscore} combo ${input.comboStatus} sync ${input.syncStatus}（原：$before）")
        return status(user)
    }

    fun recent() = repos.userClaimLog.findTop300ByOrderByIdDesc().map {
        mapOf(
            "id" to it.id,
            "createdAt" to it.createdAt.toString().replace('T', ' '),
            "userName" to it.user.userName,
            "userId" to it.user.card?.extId,
            "kind" to it.kind,
            "musicId" to it.musicId,
            "level" to it.level,
            "oldValue" to it.oldValue,
            "newValue" to it.newValue,
            "detail" to it.detail,
        )
    }
}
