package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenVisualTest {

    // Distinct sentinel colors so each mapping is unambiguous in assertions.
    private val palette = TokenPalette(
        accent = Color(0xFF000001),
        accentBg = Color(0xFF000002),
        text2 = Color(0xFF000003),
        text3 = Color(0xFF000004),
        expense = Color(0xFF000005),
        income = Color(0xFF000006),
        warn = Color(0xFF000007),
        accent2 = Color(0xFF000008),
        defaultShape = RoundedCornerShape(99.dp),
    )

    private fun tokens(vararg roles: TokenRole?) =
        roles.mapIndexed { i, role -> Token(text = "t$i", role = role) }

    @Test
    fun `unmarked token outside selection renders flat`() {
        val visual = tokenVisual(0, tokens(null), selection = null, palette = palette)

        assertEquals(Color.Transparent, visual.fillColor)
        assertEquals(palette.text2, visual.textColor)
        assertEquals(palette.defaultShape, visual.shape)
        assertNull(visual.border)
        assertEquals(false, visual.emphasized)
        assertEquals("", visual.stateDesc)
    }

    @Test
    fun `role styling uses the role color and a 1dp border`() {
        val visual = tokenVisual(0, tokens(TokenRole.AMOUNT), selection = null, palette = palette)

        assertEquals(palette.income.copy(alpha = 0.15f), visual.fillColor)
        assertEquals(palette.income, visual.textColor)
        assertEquals(TokenBorder(1.dp, palette.income.copy(alpha = 0.5f)), visual.border)
        assertEquals(true, visual.emphasized)
        assertEquals("marcado como Valor", visual.stateDesc)
    }

    @Test
    fun `selection wins over role`() {
        // Token has a role, but it's inside the active selection: selection styling must take over.
        val visual = tokenVisual(0, tokens(TokenRole.AMOUNT), selection = 0..0, palette = palette)

        assertEquals(palette.accentBg, visual.fillColor)
        assertEquals(palette.accent, visual.textColor)
        assertEquals(TokenBorder(2.dp, palette.accent), visual.border)
        assertEquals(true, visual.emphasized)
        assertEquals("selecionado", visual.stateDesc)
    }

    @Test
    fun `a role run groups into a single pill via end caps and interior seams`() {
        // Three consecutive AMOUNT tokens: caps on the ends, seams between.
        val run = tokens(TokenRole.AMOUNT, TokenRole.AMOUNT, TokenRole.AMOUNT)

        assertEquals(leadingCap(), tokenVisual(0, run, null, palette).shape)
        assertEquals(allSeams(), tokenVisual(1, run, null, palette).shape)
        assertEquals(trailingCap(), tokenVisual(2, run, null, palette).shape)
    }

    @Test
    fun `a lone role token is fully rounded`() {
        // Neighbors have different roles, so the run length is 1 -> caps on both ends.
        val run = tokens(TokenRole.MERCHANT, TokenRole.AMOUNT, TokenRole.DATE)

        assertEquals(fullPill(), tokenVisual(1, run, null, palette).shape)
    }

    @Test
    fun `selection groups by its own bounds, ignoring roles`() {
        val all = tokens(null, null, null)

        assertEquals(leadingCap(), tokenVisual(0, all, 0..2, palette).shape)
        assertEquals(allSeams(), tokenVisual(1, all, 0..2, palette).shape)
        assertEquals(trailingCap(), tokenVisual(2, all, 0..2, palette).shape)
    }

    // Mirror of the private spanShape contract: cap = 10dp end corners, seam = 2dp interior corners.
    private fun fullPill() = RoundedCornerShape(10.dp)
    private fun allSeams() = RoundedCornerShape(2.dp)
    private fun leadingCap() =
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 2.dp, bottomEnd = 2.dp)
    private fun trailingCap() =
        RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 10.dp, bottomEnd = 10.dp)
}
