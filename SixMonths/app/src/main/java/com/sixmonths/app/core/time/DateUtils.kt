package com.sixmonths.app.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Streak logic always uses [LocalDate] in the device timezone. Never UTC timestamps and
 * never java.util.Date: a missed day is decided by the local calendar, not by 24h spans.
 */
object DateUtils {

    private val shortDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    private val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun today(): LocalDate = LocalDate.now()

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toEpochMillis(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun parseOrNull(value: String?): LocalDate? = value?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    fun iso(date: LocalDate): String = date.toString()

    fun formatShort(date: LocalDate): String = shortDateFormat.format(date)

    fun formatMonthYear(date: LocalDate): String = monthYearFormat.format(date)

    fun daysSince(earlier: LocalDate, today: LocalDate = today()): Long =
        ChronoUnit.DAYS.between(earlier, today)

    fun isSameOrAfterToday(epochMillis: Long): Boolean = toLocalDate(epochMillis) >= today()

    /** "Today", "Tomorrow" or the weekday + date, for the Day Editor header. */
    fun relativeLabel(date: LocalDate): String {
        val today = today()
        return when {
            date == today -> "Today"
            date == today.plusDays(1L) -> "Tomorrow"
            else -> dayName(date) + ", " + formatShort(date)
        }
    }

    fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

    /** Next occurrence of [dayOfWeek] (1 = Monday ... 7 = Sunday), today included. */
    fun nextOccurrence(dayOfWeek: Int, today: LocalDate = today()): LocalDate =
        today.plusDays((((dayOfWeek - today.dayOfWeek.value) + 7) % 7).toLong())

    fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
    }

    fun formatClock(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        return String.format(Locale.ENGLISH, "%02d:%02d", time.hour, time.minute)
    }

    /** Parses "HH:mm"; returns [fallback] on malformed input instead of crashing. */
    fun parseReminderTime(value: String, fallback: LocalTime = LocalTime.of(19, 0)): LocalTime =
        runCatching { LocalTime.parse(value) }.getOrDefault(fallback)

    fun minutesUntil(target: java.time.ZonedDateTime): Long =
        Duration.between(java.time.ZonedDateTime.now(), target).toMinutes()
}
