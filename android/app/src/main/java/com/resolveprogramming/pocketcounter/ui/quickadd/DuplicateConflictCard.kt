package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

/**
 * The backend refused the create as a repeat. The body restates the draft on screen rather than
 * fetching the existing row, which would spend a request to say what the user is already reading.
 */
@Composable
fun DuplicateConflictCard(
    amount: BigDecimal?,
    name: String?,
    date: LocalDate?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PocketTheme.colors
    PocketCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        backgroundColor = colors.warnBg,
        borderColor = colors.warn,
        elevated = false,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.text2,
                )
                Column {
                    Text(
                        text = "Parece repetido",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = conflictBody(amount, name, date),
                        style = PocketTheme.typography.bodySm,
                        color = colors.text2,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketButton(
                    text = "Lançar mesmo assim",
                    onClick = onConfirm,
                    size = PocketButtonSize.SMALL,
                )
                PocketButton(
                    text = "Cancelar",
                    onClick = onCancel,
                    variant = PocketButtonVariant.GHOST,
                    size = PocketButtonSize.SMALL,
                )
            }
        }
    }
}

private fun conflictBody(amount: BigDecimal?, name: String?, date: LocalDate?): String {
    val value = amount?.let(::formatBrl).orEmpty()
    val title = name?.takeIf { it.isNotBlank() }.orEmpty()
    val day = date?.format(DAY_MONTH).orEmpty()
    return "Já existe um lançamento de $value em $title no dia $day."
}
