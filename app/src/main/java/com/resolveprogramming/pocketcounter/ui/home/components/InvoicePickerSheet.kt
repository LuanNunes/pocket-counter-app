package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.home.InvoicePickerState
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Select first, commit second. The amount-mismatch note has to appear between choosing a row and
 * writing to the ledger, and a tap-to-commit list has no such moment.
 */
@Composable
fun InvoicePickerSheet(
    picker: InvoicePickerState,
    lookups: LedgerLookups,
    onConfirm: (HistoryItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PocketTheme.colors
    val prompt = picker.prompt
    val presentation = remember(prompt, lookups) { invoicePickerPresentation(prompt, lookups) }
    var selectedId by remember(prompt.notificationId) { mutableStateOf<String?>(null) }
    val selected = prompt.candidates.firstOrNull { it.id == selectedId }
    val mismatch = selected?.let { invoiceMismatchNote(prompt.notification.parsed.amount, it) }

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = presentation.title,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        // The reference amount is what every row is compared against, so it is emphasised rather
        // than left in the dimmest style. `subtitle` remains the spoken form for screen readers.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = presentation.subtitle
            },
        ) {
            Text(
                text = "Pagamento de",
                style = PocketTheme.typography.bodySm,
                color = colors.text3,
            )
            presentation.referenceAmount?.let { amount ->
                Spacer(Modifier.width(6.dp))
                AmountText(
                    amount = amount,
                    style = PocketTheme.typography.monoBody.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            presentation.issuer?.let { issuer ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· $issuer",
                    style = PocketTheme.typography.bodySm,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (presentation.groups.isEmpty()) {
            PickerEmptyState()
        }
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presentation.groups.forEach { group ->
                group.header?.let { header ->
                    Text(
                        text = header,
                        style = PocketTheme.typography.label,
                        color = colors.text3,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .semantics { heading() },
                    )
                }
                group.rows.forEach { row ->
                    InvoicePickerRowItem(
                        row = row,
                        selected = row.id == selectedId,
                        enabled = !picker.isConfirming,
                        onSelect = { selectedId = row.id },
                    )
                }
            }
        }

        val reducedMotion = LocalReducedMotion.current
        AnimatedVisibility(
            visible = mismatch != null,
            enter = EnterTransition.None.takeIf { reducedMotion } ?: (expandVertically() + fadeIn()),
            exit = ExitTransition.None.takeIf { reducedMotion } ?: (shrinkVertically() + fadeOut()),
        ) {
            mismatch?.let { MismatchNote(it) }
        }

        picker.errorMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = PocketTheme.typography.bodySm,
                color = colors.expense,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.expenseBg, PocketTheme.shapes.chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
        Spacer(Modifier.height(12.dp))
        PocketButton(
            text = "Confirmar pagamento".takeUnless { picker.isConfirming } ?: "Confirmando…",
            onClick = { selected?.let(onConfirm) },
            enabled = selected != null && !picker.isConfirming,
            fillMaxWidth = true,
            modifier = Modifier.heightIn(min = 52.dp),
            leading = ctaSpinner().takeIf { picker.isConfirming },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InvoicePickerRowItem(
    row: InvoicePickerRow,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = PocketTheme.colors
    val shape = PocketTheme.shapes.labelPicker
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.accentBg.takeIf { selected } ?: colors.surface, shape)
            .border(1.dp, colors.accent.takeIf { selected } ?: colors.line2, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = row.contentDescription }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // Unsigned: every row is an expense, and the − adds a glyph to a column whose only
                // job is being scanned for a numeric match.
                AmountText(
                    amount = row.amount,
                    style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.meta,
                    style = PocketTheme.typography.bodyXs,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(label = row.statusLabel)
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.accent,
            )
        }
    }
}

/** Quiet neutral row with a warn glyph, never a filled amber block (the bundles are explicit). */
@Composable
private fun MismatchNote(note: InvoiceMismatchNote) {
    val colors = PocketTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(colors.surface2, PocketTheme.shapes.chip)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.warn,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = mismatchText(note),
            style = PocketTheme.typography.bodySm,
            color = colors.text2,
        )
    }
}

private fun mismatchText(note: InvoiceMismatchNote): AnnotatedString = buildAnnotatedString {
    append(note.prefix)
    note.emphasis?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) } }
    append(note.suffix)
}

@Composable
private fun PickerEmptyState() {
    val colors = PocketTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nenhuma fatura pendente.",
            style = PocketTheme.typography.bodySm,
            color = colors.text3,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Faturas aparecem aqui quando um cartão fecha.",
            style = PocketTheme.typography.bodySm,
            color = colors.text3.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun ctaSpinner(): @Composable () -> Unit = {
    CircularProgressIndicator(
        modifier = Modifier.size(14.dp),
        strokeWidth = 2.dp,
        color = PocketTheme.colors.accentInk,
    )
}
