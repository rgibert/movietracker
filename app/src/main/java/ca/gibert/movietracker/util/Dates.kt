package ca.gibert.movietracker.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

fun parseTraktInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
        }
        .getOrNull()
}

fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    atZone(zone).toLocalDate()

fun isAired(firstAired: String?, now: Instant = Instant.now()): Boolean {
    val instant = parseTraktInstant(firstAired) ?: return true
    return !instant.isAfter(now)
}

fun formatRelativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()),
        )
    }

fun formatShortDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.localDate(zone)
    val pattern = if (date.year == LocalDate.now(zone).year) "MMM d" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun formatFullDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(zone).toLocalDate())

/**
 * Every day cell shown for [month] in a month grid, including the trailing days
 * of the previous month and leading days of the next so that each row has seven
 * entries. The grid starts on [firstDayOfWeek] (Monday by default outside the
 * US) and grows to as many whole weeks as the month needs.
 */
fun monthCalendarDays(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
): List<LocalDate> {
    val firstOfMonth = month.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cellCount = ((leadingDays + month.lengthOfMonth() + 6) / 7) * 7
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
    return (0 until cellCount).map { gridStart.plusDays(it.toLong()) }
}

fun formatTime(instant: Instant, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return instant.atZone(zone).format(
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()),
    )
}

fun formatAirDateTime(
    instant: Instant,
    is24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val dateLabel = formatRelativeDay(instant.localDate(zone))
    return "$dateLabel · ${formatTime(instant, is24Hour, zone)}"
}

fun formatDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun formatMonth(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

/** "In 3 days", "Tomorrow", "Today" for near dates; otherwise null. */
fun relativeDaysLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String? {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..13 -> "In $days days"
        else -> null
    }
}

/** A runtime in minutes as "1h 52m". */
fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
