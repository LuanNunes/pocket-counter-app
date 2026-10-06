package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** Uppercase field label (CSS `.fld-label`). */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = PocketTheme.typography.label,
        color = PocketTheme.colors.text3,
        modifier = modifier,
    )
}

/**
 * Inline save error for a form inside a modal sheet, where a toast would render behind the scrim.
 * Announced politely so it reaches TalkBack without stealing focus from the field being edited.
 */
@Composable
fun FormErrorNote(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = PocketTheme.typography.bodySm,
        color = PocketTheme.colors.expense,
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.expenseBg, PocketTheme.shapes.chip)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 48dp bordered text input (CSS `.fld-input`). */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = PocketTheme.typography.body.copy(color = PocketTheme.colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        cursorBrush = SolidColor(PocketTheme.colors.accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, PocketTheme.colors.line2, PocketTheme.shapes.labelPicker)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.labelPicker)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
                }
                inner()
            }
        },
    )
}

/** Label + Material switch row (CSS `.fld` + `.mswitch`). */
@Composable
fun FormSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PocketTheme.typography.body, color = PocketTheme.colors.text)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PocketTheme.colors.accent,
                checkedThumbColor = PocketTheme.colors.accentInk,
            ),
        )
    }
}
