package icu.samnyan.aqua.sega.maimai2

import ext.jstNow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Validity period of a newly bought Magical Pass.
 *
 * The client treats a pass as active only when the cabinet's play time (JST) is
 * between startDate and endDate. Using the purchase second as startDate makes the
 * pass look "not started yet" on cabinets whose clock is a few seconds behind the
 * server, so the start is anchored to the beginning of the game day instead
 * (07:00 JST, ConstParameter.DefaultMaintenanceEndTime in the client).
 */
object Mai2MagicalPass {
    const val TICKET_ITEM_ID = 40001
    /** Tickets that come with one pass, as in the original game. */
    const val TICKETS_PER_PASS = 5
    const val VALID_DAYS = 14L
    val GAME_DAY_START: LocalTime = LocalTime.of(7, 0)
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")

    data class Period(val start: LocalDateTime, val end: LocalDateTime) {
        val startDate: String get() = start.format(DATE_FORMAT)
        val endDate: String get() = end.format(DATE_FORMAT)
    }

    fun gameDayStart(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(GAME_DAY_START)
        return if (now.isBefore(today)) today.minusDays(1) else today
    }

    fun newPeriod(now: LocalDateTime = jstNow()) = Period(gameDayStart(now), now.plusDays(VALID_DAYS))
}
