package com.resolveprogramming.pocketcounter.ui.assistente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSanitizeTest {

    @Test
    fun `raw HTML is kept as literal text, never turned into a node`() {
        val blocks = parseMarkdown("<script>alert(1)</script>")
        assertEquals(1, blocks.size)
        val para = blocks[0] as MdBlock.Para
        // The angle brackets survive verbatim — the parser has no HTML production.
        assertTrue(para.text.contains("<script>"))
        assertTrue(para.text.contains("</script>"))
    }

    @Test
    fun `an html img onerror payload is just text`() {
        val blocks = parseMarkdown("Veja: <img src=x onerror=alert(1)>")
        val para = blocks.first() as MdBlock.Para
        assertTrue(para.text.contains("<img src=x onerror=alert(1)>"))
    }

    @Test
    fun `pipe tables parse into a Table block`() {
        val md = """
            | Mês | Total |
            | --- | --- |
            | jan | 100 |
            | fev | 200 |
        """.trimIndent()
        val table = parseMarkdown(md).single() as MdBlock.Table
        assertEquals(listOf("Mês", "Total"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("fev", "200"), table.rows[1])
    }

    @Test
    fun `bullets and headings parse`() {
        val md = "# Resumo\n\n- um\n- dois"
        val blocks = parseMarkdown(md)
        assertTrue(blocks[0] is MdBlock.Heading)
        val bullets = blocks[1] as MdBlock.Bullets
        assertEquals(listOf("um", "dois"), bullets.items)
    }

    @Test
    fun `blockquote markers are stripped, rendered as a paragraph`() {
        val para = parseMarkdown("> Esse valor exclui cartão de crédito.").single() as MdBlock.Para
        assertTrue(para.text.startsWith("Esse valor"))
        assertTrue(!para.text.contains(">"))
    }

    @Test
    fun `inline bold renders the inner text without the markers`() {
        val s = inline("isto é **importante**")
        assertEquals("isto é importante", s.text)
    }
}
