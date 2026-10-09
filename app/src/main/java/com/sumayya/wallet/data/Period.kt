package com.sumayya.wallet.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class Period(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    LIFETIME("Lifetime");

    /** Inclusive [from, to] range in epoch millis for the period containing today. */
    fun range(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val (start, endExclusive) = when (this) {
            WEEK -> {
                val s = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                s to s.plusWeeks(1)
            }
            MONTH -> {
                val s = today.withDayOfMonth(1)
                s to s.plusMonths(1)
            }
            YEAR -> {
                val s = today.withDayOfYear(1)
                s to s.plusYears(1)
            }
            LIFETIME -> return 0L to Long.MAX_VALUE
        }
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }
}
