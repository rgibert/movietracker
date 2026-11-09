package ca.gibert.movietracker.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Trakt rejects comments shorter than this. */
const val MIN_REVIEW_WORDS = 5

/** Trakt marks comments longer than this as reviews. */
const val REVIEW_WORDS = 200

fun wordCount(text: String): Int =
    text.trim().split(Regex("""\s+""")).count { it.isNotBlank() }

/**
 * The timestamp for a watch logged on [date]. Today's watches are stamped now;
 * earlier dates get an evening time so the entry lands on the chosen day in
 * every nearby time zone.
 */
fun watchedAtFor(date: LocalDate, clock: Clock = Clock.systemDefaultZone()): Instant {
    val today = LocalDate.now(clock)
    return if (!date.isBefore(today)) {
        clock.instant()
    } else {
        date.atTime(20, 0).atZone(clock.zone).toInstant()
    }
}
