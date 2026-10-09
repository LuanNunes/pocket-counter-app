package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun DpRect.span(): Float = right.value - left.value

/** "Editar frase" has to survive the narrowest phone and the largest text, unbroken. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddFooterTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                PocketTheme {
                    Box(Modifier.requiredWidth(360.dp)) {
                        QuickAddFooter(
                            secondaryText = "Editar frase",
                            onSecondary = {},
                            primaryText = "Lançar",
                            onPrimary = {},
                            primaryEnabled = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the two actions share the row in equal halves`() {
        render(fontScale = 1f)

        val secondary = compose.onNodeWithText("Editar frase").getUnclippedBoundsInRoot()
        val primary = compose.onNodeWithText("Lançar").getUnclippedBoundsInRoot()

        assertEquals(secondary.top.value, primary.top.value, 0.5f)
        assertEquals(secondary.span(), primary.span(), 0.5f)
    }

    @Test
    fun `a large font scale stacks them instead of squeezing the label`() {
        render(fontScale = 1.6f)

        val secondary = compose.onNodeWithText("Editar frase").getUnclippedBoundsInRoot()
        val primary = compose.onNodeWithText("Lançar").getUnclippedBoundsInRoot()

        assertTrue(secondary.top.value >= primary.bottom.value)
        assertEquals(secondary.span(), primary.span(), 0.5f)
    }
}
