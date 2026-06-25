package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import kotlinx.coroutines.delay

class PocketToastState {
    var message: String? by mutableStateOf(null)
        private set

    suspend fun show(message: String) {
        this.message = message
        delay(2500)
        this.message = null
    }
}

@Composable
fun PocketToastHost(
    state: PocketToastState,
    modifier: Modifier = Modifier,
) {
    val colors = PocketTheme.colors
    val reducedMotion = LocalReducedMotion.current
    val message = state.message
    val slideOffset = with(LocalDensity.current) { 12.dp.roundToPx() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = run {
                if (reducedMotion) return@run fadeIn(animationSpec = tween(0))
                slideInVertically(animationSpec = tween(250)) { slideOffset } +
                    fadeIn(animationSpec = tween(250))
            },
            exit = run {
                if (reducedMotion) return@run fadeOut(animationSpec = tween(0))
                slideOutVertically(animationSpec = tween(250)) { slideOffset } +
                    fadeOut(animationSpec = tween(250))
            },
            modifier = Modifier.padding(bottom = 80.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Medium),
                color = colors.bg,
                modifier = Modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = PocketTheme.shapes.pill,
                        ambientColor = Color(0x33141428),
                        spotColor = Color(0x4D141428),
                    )
                    .background(colors.text, PocketTheme.shapes.pill)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
