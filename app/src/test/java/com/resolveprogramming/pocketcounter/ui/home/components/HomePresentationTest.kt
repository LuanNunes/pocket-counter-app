package com.resolveprogramming.pocketcounter.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomePresentationTest {

    // -- figureOrDash --

    @Test
    fun `figureOrDash withholds the value until it is known`() {
        assertEquals(UNKNOWN_FIGURE, figureOrDash("R$ 0,00", hasLoaded = false))
        assertEquals("R$ 0,00", figureOrDash("R$ 0,00", hasLoaded = true))
    }

    // -- homeGreeting --

    @Test
    fun `homeGreeting falls back for a blank name, so the avatar initial is never a question mark`() {
        assertEquals("Bem-vindo", homeGreeting(""))
        assertEquals("Bem-vindo", homeGreeting("   "))
        assertEquals("B", homeGreeting("   ").first().uppercase())
    }

    @Test
    fun `homeGreeting trims, so a leading space is not the initial`() {
        assertEquals("ana", homeGreeting("  ana "))
        assertEquals("A", homeGreeting("  ana ").first().uppercase())
    }

    // -- heroPendingDescription --

    @Test
    fun `heroPendingDescription reads carregando while loading`() {
        val description = heroPendingDescription(
            monthLabel = "Agosto 2026",
            formattedPending = "R$ 100,00",
            hasLoaded = false,
        )

        assertEquals("Pendente de Agosto 2026, carregando", description)
    }

    @Test
    fun `heroPendingDescription reads the value once loaded`() {
        val description = heroPendingDescription(
            monthLabel = "Agosto 2026",
            formattedPending = "R$ 100,00",
            hasLoaded = true,
        )

        assertEquals("Pendente de Agosto 2026, R$ 100,00", description)
    }

    // -- kpiRowDescription --

    @Test
    fun `kpiRowDescription reads carregando while loading`() {
        val description = kpiRowDescription(
            label = "Despesas",
            formattedValue = "R$ 100,00",
            count = 2,
            hasLoaded = false,
        )

        assertEquals("Despesas, carregando", description)
    }

    @Test
    fun `kpiRowDescription is singular for a single lancamento once loaded`() {
        val description = kpiRowDescription(
            label = "Despesas",
            formattedValue = "R$ 100,00",
            count = 1,
            hasLoaded = true,
        )

        assertEquals("Despesas, R$ 100,00, 1 lançamento", description)
    }

    @Test
    fun `kpiRowDescription is plural for more than one lancamento once loaded`() {
        val description = kpiRowDescription(
            label = "Receitas",
            formattedValue = "R$ 300,00",
            count = 5,
            hasLoaded = true,
        )

        assertEquals("Receitas, R$ 300,00, 5 lançamentos", description)
    }

    // -- swipeCueLabel: the visible sub-line under "Lançamentos" --

    @Test
    fun `swipeCueLabel is the unknown-figure placeholder while loading`() {
        val label = swipeCueLabel(count = 0, hasLoaded = false)

        assertEquals(UNKNOWN_FIGURE, label)
    }

    @Test
    fun `swipeCueLabel invites a swipe once loaded with an empty month`() {
        val label = swipeCueLabel(count = 0, hasLoaded = true)

        assertEquals("deslize para ver", label)
    }

    @Test
    fun `swipeCueLabel shows the month count once loaded with items`() {
        val label = swipeCueLabel(count = 5, hasLoaded = true)

        assertEquals("5 no mês", label)
    }

    // -- swipeCueDescription --

    @Test
    fun `swipeCueDescription reads carregando while loading`() {
        val description = swipeCueDescription(count = 0, hasLoaded = false)

        assertEquals("Lançamentos, carregando", description)
    }

    @Test
    fun `swipeCueDescription reads the month count once loaded`() {
        val description = swipeCueDescription(count = 5, hasLoaded = true)

        assertEquals("Lançamentos, 5 no mês", description)
    }

    @Test
    fun `swipeCueDescription announces the visible label on a loaded empty month`() {
        // Voice Access acts on what is on screen; "0 no mês" is not on screen.
        assertEquals("Lançamentos, deslize para ver", swipeCueDescription(count = 0, hasLoaded = true))
    }

    // -- faturasTileDescription --

    @Test
    fun `faturasTileDescription reads carregando while loading`() {
        val description = faturasTileDescription(formattedTotal = "R$ 200,00", cardCount = 2, isLoading = true)

        assertEquals("Faturas, carregando", description)
    }

    @Test
    fun `faturasTileDescription is singular for a single cartao once loaded`() {
        val description = faturasTileDescription(formattedTotal = "R$ 200,00", cardCount = 1, isLoading = false)

        assertEquals("Faturas, R$ 200,00, 1 cartão", description)
    }

    @Test
    fun `faturasTileDescription is plural for more than one cartao once loaded`() {
        val description = faturasTileDescription(formattedTotal = "R$ 200,00", cardCount = 3, isLoading = false)

        assertEquals("Faturas, R$ 200,00, 3 cartões", description)
    }

    // -- resumoTileDescription --

    @Test
    fun `resumoTileDescription mirrors the tile's caption and value`() {
        assertEquals("Resumo do mês, Para onde foi", resumoTileDescription())
    }

    // -- the visual abbreviation must never leak into a spoken description --

    @Test
    fun `no contentDescription carries the visual lançs abbreviation`() {
        val descriptions = listOf(
            heroPendingDescription("Agosto 2026", "R$ 100,00", hasLoaded = false),
            heroPendingDescription("Agosto 2026", "R$ 100,00", hasLoaded = true),
            kpiRowDescription("Despesas", "R$ 100,00", count = 2, hasLoaded = false),
            kpiRowDescription("Despesas", "R$ 100,00", count = 2, hasLoaded = true),
            swipeCueDescription(count = 2, hasLoaded = false),
            swipeCueDescription(count = 2, hasLoaded = true),
            faturasTileDescription("R$ 200,00", cardCount = 2, isLoading = true),
            faturasTileDescription("R$ 200,00", cardCount = 2, isLoading = false),
            resumoTileDescription(),
        )

        descriptions.forEach { description -> assertFalse(description.contains("lançs.")) }
    }
}
