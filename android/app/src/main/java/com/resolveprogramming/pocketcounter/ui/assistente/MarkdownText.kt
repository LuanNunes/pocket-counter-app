package com.resolveprogramming.pocketcounter.ui.assistente

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Renders a SAFE subset of Markdown. The parser has NO HTML production — any `<...>` is treated
 * as literal text — so raw HTML from the LLM is never interpreted, only ever shown as characters.
 * Supported: headings, paragraphs, bold/italic, inline code, fenced code, bullet lists, tables.
 */
@Composable
fun MarkdownAnswer(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { RenderBlock(it) }
    }
}

internal sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
}

@Composable
private fun RenderBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            inline(block.text),
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Bold).takeIf { block.level <= 1 }
                ?: PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
        is MdBlock.Para -> Text(inline(block.text), style = PocketTheme.typography.body, color = PocketTheme.colors.text2)
        is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•  ", style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
                    Text(inline(item), style = PocketTheme.typography.body, color = PocketTheme.colors.text2)
                }
            }
        }
        is MdBlock.Code -> Text(
            block.text,
            style = PocketTheme.typography.monoSm,
            color = PocketTheme.colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketTheme.colors.surface2, PocketTheme.shapes.chip)
                .padding(12.dp),
        )
        is MdBlock.Table -> MarkdownTable(block)
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val colWidth = 132.dp
    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip),
    ) {
        Row(modifier = Modifier.background(PocketTheme.colors.surface2)) {
            table.headers.forEach { h ->
                Text(
                    h.uppercase(),
                    style = PocketTheme.typography.label,
                    color = PocketTheme.colors.text3,
                    modifier = Modifier.width(colWidth).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        table.rows.forEach { row ->
            HorizontalDivider(color = PocketTheme.colors.line)
            Row {
                table.headers.indices.forEach { c ->
                    Text(
                        row.getOrElse(c) { "" },
                        style = PocketTheme.typography.bodySm,
                        color = PocketTheme.colors.text2,
                        modifier = Modifier.width(colWidth).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ── Parsing (pure, no Compose) ───────────────────────────────────────────

internal fun parseMarkdown(md: String): List<MdBlock> {
    val lines = md.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        if (line.isBlank()) {
            i++
            continue
        }

        if (trimmed.startsWith("```")) {
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i]); i++
            }
            i++ // skip closing fence
            blocks += MdBlock.Code(code.toString().trimEnd())
            continue
        }

        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            blocks += MdBlock.Heading(level, trimmed.dropWhile { it == '#' }.trim())
            i++
            continue
        }

        if (isTableRow(line) && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val headers = splitRow(line)
            i += 2 // header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && isTableRow(lines[i])) {
                rows += splitRow(lines[i]); i++
            }
            blocks += MdBlock.Table(headers, rows)
            continue
        }

        if (trimmed.startsWith(">")) {
            // Blockquotes aren't a distinct block here — strip the marker, render as a paragraph.
            val quote = StringBuilder()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                val stripped = lines[i].trimStart().removePrefix(">").trimStart()
                if (quote.isNotEmpty()) quote.append(' ')
                quote.append(stripped)
                i++
            }
            if (quote.isNotEmpty()) blocks += MdBlock.Para(quote.toString())
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val items = mutableListOf<String>()
            while (i < lines.size && (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* "))) {
                items += lines[i].trimStart().drop(2).trim(); i++
            }
            blocks += MdBlock.Bullets(items)
            continue
        }

        val para = StringBuilder()
        while (i < lines.size && lines[i].isNotBlank() && !lines[i].trimStart().startsWith("#") &&
            !lines[i].trimStart().startsWith("```") && !isTableRow(lines[i]) &&
            !lines[i].trimStart().startsWith(">") &&
            !lines[i].trimStart().startsWith("- ") && !lines[i].trimStart().startsWith("* ")
        ) {
            if (para.isNotEmpty()) para.append(' ')
            para.append(lines[i].trim()); i++
        }
        if (para.isNotEmpty()) blocks += MdBlock.Para(para.toString())
    }
    return blocks
}

private fun isTableRow(line: String): Boolean = line.trim().startsWith("|") && line.contains('|')
private fun isTableSeparator(line: String): Boolean =
    line.trim().startsWith("|") && line.replace("|", "").trim().all { it == '-' || it == ':' || it == ' ' } &&
        line.contains('-')

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }

/** Inline bold/italic/code → AnnotatedString. Treats `<`/`>` as literal (never HTML). */
internal fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            if (end <= 0) { append("**"); i += 2 }
            continue
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > 0) {
                withStyle(SpanStyle(fontFamily = com.resolveprogramming.pocketcounter.ui.theme.GeistMono)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
            }
            if (end <= 0) { append('`'); i++ }
            continue
        }
        if (text[i] == '*' || text[i] == '_') {
            val marker = text[i]
            val end = text.indexOf(marker, i + 1)
            if (end > 0) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            if (end <= 0) { append(marker); i++ }
            continue
        }
        append(text[i]); i++
    }
}
