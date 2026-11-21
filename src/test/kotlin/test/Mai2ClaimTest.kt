package test

import icu.samnyan.aqua.net.games.GenericMusicMeta
import icu.samnyan.aqua.net.games.GenericNoteMeta
import icu.samnyan.aqua.sega.maimai2.Mai2Claim
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserMusicDetail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private val meta = GenericMusicMeta("Song", "Ver1.00.00", listOf(GenericNoteMeta(5.0), GenericNoteMeta(7.0), GenericNoteMeta(10.0), GenericNoteMeta(12.0), GenericNoteMeta(null)))
private fun input(
    music: Int = 8, level: Int = 3, ach: Int = 990000, dx: Int = 1500, combo: Int = 0, sync: Int = 0,
) = Mai2Claim.ScoreInput(music, level, ach, dx, combo, sync)

class Mai2ClaimTest : StringSpec({
    "rank follows the achievement thresholds" {
        Mai2Claim.scoreRank(1010000) shouldBe 13
        Mai2Claim.scoreRank(1005000) shouldBe 13
        Mai2Claim.scoreRank(1004999) shouldBe 12
        Mai2Claim.scoreRank(983640) shouldBe 9
        Mai2Claim.scoreRank(972161) shouldBe 8
        Mai2Claim.scoreRank(800000) shouldBe 5
        Mai2Claim.scoreRank(499999) shouldBe 0
    }

    "a normal submission is accepted" {
        Mai2Claim.validate(meta, input()) shouldBe null
    }

    "unknown songs, missing charts and out of range values are rejected" {
        Mai2Claim.validate(null, input()) shouldBe "找不到这首歌"
        Mai2Claim.validate(meta, input(music = 100001)) shouldBe "找不到这首歌"
        Mai2Claim.validate(meta, input(level = 4)) shouldBe "这首歌没有所选难度"
        Mai2Claim.validate(meta, input(level = 5)) shouldBe "这首歌没有所选难度"
        Mai2Claim.validate(meta, input(ach = 1010001)) shouldBe "达成率必须在 0% 到 101% 之间"
        Mai2Claim.validate(meta, input(ach = -1)) shouldBe "达成率必须在 0% 到 101% 之间"
        Mai2Claim.validate(meta, input(dx = 10000)) shouldBe "DX 分数不合理"
        Mai2Claim.validate(meta, input(combo = 5)) shouldBe "FC/AP 或 FS/FDX 状态不合法"
    }

    "AP needs a perfect enough achievement" {
        Mai2Claim.validate(meta, input(ach = 990000, combo = 3)) shouldBe "AP 成绩的达成率不可能低于 100%"
        Mai2Claim.validate(meta, input(ach = 1005000, combo = 3)) shouldBe null
        Mai2Claim.validate(meta, input(ach = 1005000, combo = 4)) shouldBe "AP+ 成绩的达成率必须是 101%"
        Mai2Claim.validate(meta, input(ach = 1010000, combo = 4)) shouldBe null
    }

    "a first result creates the row and counts as a play" {
        val user = Mai2UserDetail()
        val row = Mai2Claim.merge(user, null, input(ach = 983640, dx = 1400, combo = 1, sync = 2))!!
        row.musicId shouldBe 8
        row.level shouldBe 3
        row.achievement shouldBe 983640
        row.scoreRank shouldBe 9
        row.deluxscoreMax shouldBe 1400
        row.comboStatus shouldBe 1
        row.syncStatus shouldBe 2
        row.playCount shouldBe 1
    }

    "an existing row can be lowered as well as raised" {
        val user = Mai2UserDetail()
        val old = Mai2UserMusicDetail().also {
            it.musicId = 8; it.level = 3; it.achievement = 995000; it.deluxscoreMax = 1600
            it.comboStatus = 2; it.syncStatus = 0; it.scoreRank = 11; it.playCount = 5
        }
        // Everything lower except FS: the stored values are replaced by the submitted ones.
        val row = Mai2Claim.merge(user, old, input(ach = 980000, dx = 1400, combo = 1, sync = 1))!!
        (row === old) shouldBe true
        row.achievement shouldBe 980000
        row.deluxscoreMax shouldBe 1400
        row.comboStatus shouldBe 1
        row.syncStatus shouldBe 1
        row.scoreRank shouldBe 9
        row.playCount shouldBe 5
    }

    "a submission identical to the stored row is ignored" {
        val old = Mai2UserMusicDetail().also {
            it.musicId = 8; it.level = 3; it.achievement = 990000; it.deluxscoreMax = 1500; it.comboStatus = 1; it.syncStatus = 1
        }
        Mai2Claim.merge(Mai2UserDetail(), old, input(ach = 990000, dx = 1500, combo = 1, sync = 1)) shouldBe null
    }
})
