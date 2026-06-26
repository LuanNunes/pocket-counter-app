package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

private data class RoleOption(
    val role: TokenRole,
    val label: String,
)

private val roleOptions = listOf(
    RoleOption(TokenRole.TYPE, "Tipo"),
    RoleOption(TokenRole.AMOUNT, "Valor"),
    RoleOption(TokenRole.PAYMENT, "Meio de pgto."),
    RoleOption(TokenRole.MERCHANT, "Estabelecim."),
    RoleOption(TokenRole.DATE, "Data"),
    RoleOption(TokenRole.INSTALLMENTS, "Parcelas"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenLabelPicker(
    token: Token,
    onRoleSelected: (TokenRole) -> Unit,
    onRemoveRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .background(PocketTheme.colors.surface, PocketTheme.shapes.labelPicker)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        roleOptions.forEach { option ->
            val bg = when (option.role) {
                TokenRole.TYPE -> PocketTheme.colors.expenseBg
                TokenRole.AMOUNT -> PocketTheme.colors.incomeBg
                TokenRole.PAYMENT -> PocketTheme.colors.accentBg
                TokenRole.MERCHANT -> PocketTheme.colors.warnBg
                TokenRole.DATE -> PocketTheme.colors.surface2
                TokenRole.INSTALLMENTS -> PocketTheme.colors.accentBg
            }
            val color = when (option.role) {
                TokenRole.TYPE -> PocketTheme.colors.expense
                TokenRole.AMOUNT -> PocketTheme.colors.income
                TokenRole.PAYMENT -> PocketTheme.colors.accent
                TokenRole.MERCHANT -> PocketTheme.colors.warn
                TokenRole.DATE -> PocketTheme.colors.text3
                TokenRole.INSTALLMENTS -> PocketTheme.colors.accent2
            }

            Text(
                text = option.label,
                style = PocketTheme.typography.bodySm,
                color = color,
                modifier = Modifier
                    .background(bg, PocketTheme.shapes.chip)
                    .clickable { onRoleSelected(option.role) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (token.role != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onRemoveRole)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = PocketTheme.colors.text3,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "remover marcação",
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text3,
                )
            }
        }
    }
}
