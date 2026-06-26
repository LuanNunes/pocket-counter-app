package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StepAmount(
    amount: BigDecimal?,
    date: LocalDate?,
    statusPayment: PaymentStatus,
    hasInstallments: Boolean,
    installmentsEnabled: Boolean,
    installmentCount: Int?,
    installmentValue: BigDecimal?,
    isFixo: Boolean,
    recurrenceDay: Int?,
    onAmountChange: (BigDecimal?) -> Unit,
    onDateTap: () -> Unit,
    onStatusChange: (PaymentStatus) -> Unit,
    onToggleInstallments: (Boolean) -> Unit,
    onToggleFixo: (Boolean) -> Unit,
    onRecurrenceDayChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var isFocused by remember { mutableStateOf(false) }
    var textValue by remember(amount) {
        mutableStateOf(amount?.let { formatAmountInput(it) } ?: "")
    }

    Column(modifier = modifier) {
        val question = run {
            if (amount != null) {
                return@run buildAnnotatedString {
                    append("Confirma o ")
                    withStyle(SpanStyle(color = PocketTheme.colors.accent, fontWeight = FontWeight.Bold)) {
                        append("valor")
                    }
                    append("?")
                }
            }
            buildAnnotatedString {
                append("Não achei o valor. ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Toque no número correto")
                }
                append(" ou digite abaixo.")
            }
        }

        Text(
            text = question,
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(20.dp))

        // Outer 4dp accent-bg ring on focus (matches the CSS `box-shadow: 0 0 0 4px accent-bg`).
        // The 4dp inset is always reserved so focusing doesn't shift layout.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    PocketTheme.colors.accentBg.takeIf { isFocused }
                        ?: androidx.compose.ui.graphics.Color.Transparent,
                    PocketTheme.shapes.card,
                )
                .padding(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = PocketTheme.colors.accent.takeIf { isFocused } ?: PocketTheme.colors.line,
                        shape = PocketTheme.shapes.card,
                    )
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "R$",
                    style = PocketTheme.typography.monoBody,
                    color = PocketTheme.colors.text3,
                )
                BasicTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        val cleaned = newValue.filter { it.isDigit() || it == ',' }
                        textValue = cleaned
                        val parsed = cleaned.replace(",", ".").toBigDecimalOrNull()
                        onAmountChange(parsed)
                    },
                    textStyle = PocketTheme.typography.monoAmountInput.copy(
                        color = PocketTheme.colors.text,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }
        }

        if (hasInstallments) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compra parcelada",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    if (installmentCount != null && installmentValue != null) {
                        Text(
                            text = "${installmentCount}x de R$ ${formatAmountInput(installmentValue)}",
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text2,
                        )
                    }
                }
                Switch(
                    checked = installmentsEnabled,
                    onCheckedChange = onToggleInstallments,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PocketTheme.colors.accent,
                        checkedThumbColor = PocketTheme.colors.accentInk,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Data",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text2,
            )
            Row(
                modifier = Modifier
                    .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill)
                    .clickable(onClick = onDateTap)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = (date ?: LocalDate.now()).format(dateFormatter),
                    style = PocketTheme.typography.monoSm,
                    color = PocketTheme.colors.text,
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = PocketTheme.colors.text,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                label = "Já efetuada",
                isSelected = statusPayment == PaymentStatus.PAID,
                dotColor = PocketTheme.colors.income,
                onClick = { onStatusChange(PaymentStatus.PAID) },
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                label = "Pendente",
                isSelected = statusPayment == PaymentStatus.PENDING,
                dotColor = PocketTheme.colors.warn,
                onClick = { onStatusChange(PaymentStatus.PENDING) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    PocketTheme.colors.accentBg.takeIf { isFixo } ?: PocketTheme.colors.surface,
                    PocketTheme.shapes.card,
                )
                .border(
                    1.dp,
                    PocketTheme.colors.accent.takeIf { isFixo } ?: PocketTheme.colors.line,
                    PocketTheme.shapes.card,
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Repete todo mês",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    Text(
                        text = "Para contas fixas como aluguel ou assinatura.",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
                Switch(
                    checked = isFixo,
                    onCheckedChange = onToggleFixo,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PocketTheme.colors.accent,
                        checkedThumbColor = PocketTheme.colors.accentInk,
                    ),
                )
            }

            if (isFixo) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dia do mês",
                        style = PocketTheme.typography.body,
                        color = PocketTheme.colors.text2,
                    )
                    RecurrenceDayField(
                        recurrenceDay = recurrenceDay,
                        onRecurrenceDayChange = onRecurrenceDayChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurrenceDayField(
    recurrenceDay: Int?,
    onRecurrenceDayChange: (Int?) -> Unit,
) {
    var text by remember(recurrenceDay) {
        mutableStateOf(recurrenceDay?.toString() ?: "")
    }
    // An entry the parser rejects (e.g. "0", "45") pushes null upstream; surface that as an
    // inline hint instead of leaving a number that looks accepted but silently doesn't apply.
    val isInvalid = text.isNotEmpty() && (text.toIntOrNull()?.let { it !in 1..31 } ?: true)

    Column(horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .background(PocketTheme.colors.surface, PocketTheme.shapes.pill)
                .border(
                    1.dp,
                    PocketTheme.colors.warn.takeIf { isInvalid } ?: PocketTheme.colors.line,
                    PocketTheme.shapes.pill,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    val cleaned = newValue.filter { it.isDigit() }.take(2)
                    text = cleaned
                    val day = cleaned.toIntOrNull()
                    onRecurrenceDayChange(day?.takeIf { it in 1..31 })
                },
                textStyle = PocketTheme.typography.monoSm.copy(color = PocketTheme.colors.text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(40.dp),
            )
        }
        if (isInvalid) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "1 a 31",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.warn,
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    isSelected: Boolean,
    dotColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selected pill = accent border + accent-bg (not a solid accent fill), per `.status-pill.on`.
    val bg = PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface
    val textColor = PocketTheme.colors.text.takeIf { isSelected } ?: PocketTheme.colors.text2
    val border = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line

    Row(
        modifier = modifier
            .border(1.dp, border, PocketTheme.shapes.chip)
            .background(bg, PocketTheme.shapes.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, PocketTheme.shapes.pill),
        )
        Text(
            text = label,
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

private fun formatAmountInput(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString().replace(".", ",")
