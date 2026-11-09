package ca.gibert.movietracker.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.gibert.movietracker.ui.components.StarRating
import ca.gibert.movietracker.ui.components.formatStars
import ca.gibert.movietracker.util.MIN_REVIEW_WORDS
import ca.gibert.movietracker.util.formatDate
import ca.gibert.movietracker.util.wordCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Logs a watch: the date, an optional rating and an optional review. Together
 * these make up a diary entry; Trakt stores them as a history play, a rating
 * and a comment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogWatchDialog(
    title: String,
    initialRating: Int?,
    onLog: (date: LocalDate, rating: Int?, review: String, spoiler: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var epochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var rating by rememberSaveable { mutableStateOf(initialRating) }
    var review by rememberSaveable { mutableStateOf("") }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(epochDay)
    val reviewValid = review.isBlank() || wordCount(review) >= MIN_REVIEW_WORDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log $title") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Watched on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { pickingDate = true }) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (date == today) "Today" else formatDate(date, today))
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Rating",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    rating?.let {
                        Text(
                            text = "${formatStars(it)} ★",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                StarRating(rating = rating, starSize = 34.dp, onRate = { rating = it })

                Spacer(Modifier.height(16.dp))
                ReviewFields(
                    text = review,
                    onTextChange = { review = it },
                    spoiler = spoiler,
                    onSpoilerChange = { spoiler = it },
                    optional = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLog(date, rating, review.trim(), spoiler) },
                enabled = reviewValid,
            ) {
                Text("Log", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (pickingDate) {
        // The picker works in UTC milliseconds at midnight.
        val todayMillis = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            epochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        }
                        pickingDate = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
