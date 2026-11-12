package test

import icu.samnyan.aqua.sega.maimai2.Mai2MagicalPass
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class Mai2MagicalPassPeriodTest : StringSpec({
    "pass bought after 07:00 JST starts at 07:00 the same day" {
        val now = LocalDateTime.of(2026, 11, 12, 16, 25, 11, 700_000_000)
        val period = Mai2MagicalPass.newPeriod(now)
        period.startDate shouldBe "2026-11-12 07:00:00.0"
        period.endDate shouldBe "2026-11-26 16:25:11.7"
    }

    "pass bought before 07:00 JST starts at 07:00 the previous day" {
        val now = LocalDateTime.of(2026, 11, 12, 3, 0)
        Mai2MagicalPass.newPeriod(now).startDate shouldBe "2026-11-11 07:00:00.0"
    }

    "start is never after the purchase time" {
        val now = LocalDateTime.of(2026, 11, 12, 7, 0)
        Mai2MagicalPass.gameDayStart(now) shouldBe now
    }
})
