package com.sumayya.wallet.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // "en-IN" gives lakh-style grouping (1,50,000), which is how Pakistani amounts are usually read.
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun money(value: Long): String {
        val sign = if (value < 0) "-" else ""
        return "${sign}Rs ${numberFormat.format(kotlin.math.abs(value))}"
    }

    fun date(millis: Long): String = dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The Material date picker works in UTC midnight; convert our local millis for it. */
    fun toPickerMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Picked date -> millis. Today keeps the current time, other days use local noon. */
    fun fromPickerMillis(pickerMillis: Long): Long {
        val picked = Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return if (picked == LocalDate.now(zone)) System.currentTimeMillis()
        else picked.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    fun localDateToMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun daysUntil(millis: Long): Long =
        java.time.temporal.ChronoUnit.DAYS.between(
            LocalDate.now(zone),
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        )
}
