package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** Grid of selectable color circles; the selected swatch gets an accent ring + check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .then(
                            run {
                                if (isSelected) {
                                    return@run Modifier
                                        .border(2.dp, PocketTheme.colors.text, CircleShape)
                                        .padding(3.dp)
                                }
                                Modifier
                            },
                        )
                        .background(Color(argb), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = PocketTheme.colors.accentInk,
                        )
                    }
                }
            }
        }
    }
}
