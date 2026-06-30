package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private const val MAX_DIGITS = 13
private val ptBrGrouping: NumberFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))

/**
 * Shared BRL money input used by the wizard amount step and the transaction form. Fully controlled by
 * [amount] — there is no local text state — so the displayed value can never drift from the source of
 * truth.
 *
 * Entry is a "cents pad": every typed digit shifts the value one place from the right (5 → 0,05;
 * 5000 → 50,00), so no decimal separator is needed. This sidesteps the locale-separator bug (a `.`-only
 * keyboard couldn't enter cents) and the multiple-comma silent-null bug, and renders pt-BR thousands
 * grouping live (1.234,56). An all-zero / empty value shows the [placeholder].
 *
 * Renders the "R$" prefix; the caller supplies the surrounding container, focus ring and [textStyle].
 */
@Composable
fun MoneyTextField(
    amount: BigDecimal?,
    onAmountChange: (BigDecimal?) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    valueColor: Color = PocketTheme.colors.text,
    placeholder: String = "0,00",
    autoFocus: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val text = formatCentsDigits(amountToDigits(amount))
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = "R$", style = PocketTheme.typography.monoBody, color = PocketTheme.colors.text3)
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            if (text.isEmpty()) {
                Text(text = placeholder, style = textStyle, color = PocketTheme.colors.text3)
            }
            BasicTextField(
                // Selection pinned to the end: the cents pad only ever appends/removes the last digit.
                value = TextFieldValue(text, TextRange(text.length)),
                onValueChange = { newValue ->
                    val digits = newValue.text.filter(Char::isDigit).take(MAX_DIGITS)
                    onAmountChange(digitsToAmount(digits))
                },
                textStyle = textStyle.copy(color = valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(PocketTheme.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
            )
        }
    }
}

/** The cents-digit string for an amount: 12.34 → "1234", 0.05 → "5", null/zero → "". */
internal fun amountToDigits(amount: BigDecimal?): String {
    if (amount == null) return ""
    val cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigInteger().abs()
    if (cents.signum() == 0) return ""
    return cents.toString().take(MAX_DIGITS)
}

/** Inverse of [amountToDigits]: "1234" → 12.34, "5" → 0.05, all-zero/empty → null. */
internal fun digitsToAmount(digits: String): BigDecimal? {
    if (digits.trimStart('0').isEmpty()) return null
    return BigDecimal(digits).movePointLeft(2)
}

/** Cents digits → pt-BR display: "1234" → "12,34", "123456" → "1.234,56", all-zero/empty → "". */
internal fun formatCentsDigits(digits: String): String {
    if (digits.trimStart('0').isEmpty()) return ""
    val cents = digits.toLong()
    val reais = cents / 100
    val cc = (cents % 100).toString().padStart(2, '0')
    return "${ptBrGrouping.format(reais)},$cc"
}
