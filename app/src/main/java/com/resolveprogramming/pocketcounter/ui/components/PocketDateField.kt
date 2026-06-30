package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Shared date control: a tappable field that opens a [PocketTheme]-styled Material 3 calendar, plus
 * "Hoje"/"Ontem" quick chips for the two dates nearly every entry uses. Used by both the wizard amount
 * step and the transaction form so the date experience stays identical.
 *
 * A null [date] renders a "Selecionar data" placeholder (never a misleading "today"); picking a chip or
 * a calendar day emits [onDateChange]. [today] is injectable for tests/previews.
 */
@Composable
fun PocketDateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var showPicker by remember { mutableStateOf(false) }
    val yesterday = today.minusDays(1)
    val display = date?.format(DATE_FORMATTER) ?: "Selecionar data"
    val a11y = date?.let { "Data: ${it.format(DATE_FORMATTER)}, toque para alterar" } ?: "Selecionar data"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(PocketTheme.shapes.labelPicker)
                .border(1.dp, PocketTheme.colors.line2, PocketTheme.shapes.labelPicker)
                .background(PocketTheme.colors.surface, PocketTheme.shapes.labelPicker)
                .clickable(role = Role.Button, onClick = { showPicker = true })
                .semantics(mergeDescendants = true) { contentDescription = a11y }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = display,
                style = PocketTheme.typography.monoSm,
                color = PocketTheme.colors.text.takeIf { date != null } ?: PocketTheme.colors.text3,
            )
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PocketTheme.colors.text3,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateQuickChip(label = "Hoje", selected = date == today, onClick = { onDateChange(today) })
            DateQuickChip(label = "Ontem", selected = date == yesterday, onClick = { onDateChange(yesterday) })
        }
    }

    if (showPicker) {
        PocketDatePickerDialog(
            initialDate = date ?: today,
            onConfirm = onDateChange,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun DateQuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // Selected = accent border + accent-bg tint (matches the wizard's StatusPill), not a solid fill.
    val bg = PocketTheme.colors.accentBg.takeIf { selected } ?: PocketTheme.colors.surface
    val textColor = PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.text2
    val border = PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.line

    Row(
        modifier = Modifier
            .clip(PocketTheme.shapes.chip)
            .border(1.dp, border, PocketTheme.shapes.chip)
            .background(bg, PocketTheme.shapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

/**
 * Material 3 calendar wrapped in the app theme. The picker works in UTC, so the initial value and the
 * confirmed selection are both converted through [ZoneOffset.UTC] to avoid an off-by-one-day shift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PocketDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state)
    }
}
