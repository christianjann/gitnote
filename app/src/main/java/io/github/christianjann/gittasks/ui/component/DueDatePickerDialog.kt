package io.github.christianjann.gittasks.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.christianjann.gittasks.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(
    currentDueDate: LocalDateTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime?) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(null) }

    val initialDateMillis = currentDueDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        ?: System.currentTimeMillis()
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis
    )

    val timePickerState = rememberTimePickerState(
        initialHour = currentDueDate?.hour ?: 12,
        initialMinute = currentDueDate?.minute ?: 0
    )

    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentDueDate != null) {
                        TextButton(
                            onClick = {
                                onConfirm(null)
                            }
                        ) {
                            Text(stringResource(R.string.clear_due_date))
                        }
                    }
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            selectedDate = datePickerState.selectedDateMillis
                            showTimePicker = true
                        }
                    ) {
                        Text(stringResource(R.string.next))
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        // Use AlertDialog for time picker since we're not in a DatePickerDialog
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.select_time),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text(stringResource(R.string.back))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dateMillis = selectedDate ?: System.currentTimeMillis()
                        val localDate = Instant.ofEpochMilli(dateMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val localDateTime = localDate.atTime(
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        onConfirm(localDateTime)
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}
