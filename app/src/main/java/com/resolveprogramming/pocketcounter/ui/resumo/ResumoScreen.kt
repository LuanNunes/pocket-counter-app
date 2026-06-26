package com.resolveprogramming.pocketcounter.ui.resumo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.CompareOption
import com.resolveprogramming.pocketcounter.domain.model.MonthlySummary
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun ResumoScreen(
    onBack: () -> Unit,
    viewModel: ResumoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Top bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = PocketTheme.colors.text,
                    )
                }
                Column {
                    Text(
                        text = "Resumo do mês",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = PocketTheme.colors.text,
                    )
                    Text(
                        text = state.monthLabel,
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Despesas / Receitas segment
        item {
            KindSegment(
                kind = state.kind,
                onSetKind = viewModel::setKind,
            )
        }

        item { Spacer(Modifier.height(16.dp)) }

        // Donut + comparison line
        item {
            val summary = state.summary
            if (summary != null) {
                PocketCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DonutChart(
                            groups = summary.groups,
                            total = summary.total,
                            kind = state.kind,
                            formatter = formatter,
                        )

                        val baseTotal = summary.baseTotal
                        val absDiff = baseTotal?.let { summary.total - it }
                        val curOpt = state.options.find { it.key == state.compareKey }
                        if (absDiff != null && curOpt != null) {
                            Spacer(Modifier.height(8.dp))
                            ComparisonLine(
                                absDiff = absDiff,
                                option = curOpt,
                                kind = state.kind,
                                formatter = formatter,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Compare selector
        item {
            if (state.options.isNotEmpty()) {
                CompareBar(
                    options = state.options,
                    selectedKey = state.compareKey,
                    onSelect = viewModel::setCompareKey,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Show previous toggle
        item {
            val curOpt = state.options.find { it.key == state.compareKey }
            val prevLabel = run {
                if (curOpt == null) return@run "comparação"
                if (curOpt.key == "avg3") return@run "referência"
                curOpt.label
            }
            ShowPrevToggle(
                label = "Mostrar valor de $prevLabel",
                checked = state.showPrev,
                onToggle = viewModel::toggleShowPrev,
            )
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Rank header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Por categoria de renda".takeIf {
                        state.kind == TransactionType.INCOME
                    } ?: "Por contexto",
                    style = PocketTheme.typography.sectionHeader,
                    color = PocketTheme.colors.text3,
                )
                Text(
                    text = "toque para detalhar",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Ranked groups
        val summary = state.summary
        if (summary != null) {
            val maxTotal = summary.groups.maxOfOrNull { it.total } ?: BigDecimal.ONE

            items(summary.groups, key = { it.id }) { group ->
                RankRow(
                    group = group,
                    max = maxTotal,
                    kind = state.kind,
                    open = group.id in state.openGroupIds,
                    onToggle = { viewModel.toggleGroup(group.id) },
                    showPrev = state.showPrev,
                    prevLabel = state.options.find { it.key == state.compareKey }?.label ?: "",
                    formatter = formatter,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Categorizado a partir das suas notificações.",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun KindSegment(
    kind: TransactionType,
    onSetKind: (TransactionType) -> Unit,
) {
    val expenseActive = kind == TransactionType.EXPENSE
    val incomeActive = kind == TransactionType.INCOME

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PocketTheme.shapes.chip)
            .background(PocketTheme.colors.surface2),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(PocketTheme.shapes.chip)
                .background(
                    PocketTheme.colors.expenseBg.takeIf { expenseActive } ?: Color.Transparent,
                )
                .clickable { onSetKind(TransactionType.EXPENSE) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Despesas",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.expense.takeIf { expenseActive } ?: PocketTheme.colors.text3,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(PocketTheme.shapes.chip)
                .background(
                    PocketTheme.colors.incomeBg.takeIf { incomeActive } ?: Color.Transparent,
                )
                .clickable { onSetKind(TransactionType.INCOME) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Receitas",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.income.takeIf { incomeActive } ?: PocketTheme.colors.text3,
            )
        }
    }
}

@Composable
private fun DonutChart(
    groups: List<SummaryGroup>,
    total: BigDecimal,
    kind: TransactionType,
    formatter: NumberFormat,
) {
    val trackColor = PocketTheme.colors.surface2
    val totalColor = PocketTheme.colors.income.takeIf { kind == TransactionType.INCOME } ?: PocketTheme.colors.expense

    Box(
        modifier = Modifier.size(168.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .drawWithContent {
                    val strokeWidth = 20.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val radius = diameter / 2f
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f,
                    )
                    val arcSize = Size(diameter, diameter)

                    // Track circle
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidth),
                    )

                    // Segments
                    var startAngle = -90f
                    groups.forEach { g ->
                        val sweep = g.pct * 360f
                        drawArc(
                            color = Color(g.color),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                        )
                        startAngle += sweep
                    }
                    drawContent()
                },
        ) {}

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Receitas".takeIf { kind == TransactionType.INCOME } ?: "Despesas",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = formatter.format(total),
                style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
                color = totalColor,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ComparisonLine(
    absDiff: BigDecimal,
    option: CompareOption,
    kind: TransactionType,
    formatter: NumberFormat,
) {
    val isPositive = absDiff >= BigDecimal.ZERO
    // For despesas: more spent = bad (red); less spent = good (green)
    // For receitas: more earned = good (green); less earned = bad (red)
    val goodDirection = isPositive.takeIf { kind == TransactionType.INCOME } ?: !isPositive
    val diffColor = PocketTheme.colors.income.takeIf { goodDirection } ?: PocketTheme.colors.expense
    val sign = "+".takeIf { isPositive } ?: "−"
    val vsLabel = "a média".takeIf { option.key == "avg3" } ?: "em ${option.label}"
    val direction = "a mais que".takeIf { isPositive } ?: "a menos que"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$sign${formatter.format(absDiff.abs())} ",
            style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
            color = diffColor,
        )
        Text(
            text = "$direction $vsLabel",
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text2,
        )
    }
}

@Composable
private fun CompareBar(
    options: List<CompareOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val curOpt = options.find { it.key == selectedKey } ?: options.firstOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Comparar com",
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text2,
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(PocketTheme.shapes.chip)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = curOpt?.label ?: "—",
                    style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PocketTheme.colors.text3,
                )
            }
            // DropdownMenu is immediately opaque — no entrance animation ending hidden
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = opt.label,
                                    style = PocketTheme.typography.body.copy(
                                        fontWeight = FontWeight.SemiBold.takeIf {
                                            opt.key == selectedKey
                                        } ?: FontWeight.Normal,
                                    ),
                                    color = PocketTheme.colors.accent.takeIf {
                                        opt.key == selectedKey
                                    } ?: PocketTheme.colors.text,
                                )
                                if (opt.key == selectedKey) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = PocketTheme.colors.accent,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(opt.key)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowPrevToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text2,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = PocketTheme.colors.accentInk,
                checkedTrackColor = PocketTheme.colors.accent,
                uncheckedThumbColor = PocketTheme.colors.text3,
                uncheckedTrackColor = PocketTheme.colors.line2,
            ),
        )
    }
}

@Composable
private fun RankRow(
    group: SummaryGroup,
    max: BigDecimal,
    kind: TransactionType,
    open: Boolean,
    onToggle: () -> Unit,
    showPrev: Boolean,
    prevLabel: String,
    formatter: NumberFormat,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PocketTheme.shapes.card)
            .background(PocketTheme.colors.surface)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card),
    ) {
        // Main row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(PocketTheme.shapes.pill)
                    .background(Color(group.color)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.name,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = PocketTheme.colors.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            DeltaBadge(delta = group.delta, invert = kind == TransactionType.INCOME)
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatter.format(group.total),
                style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore.takeIf { open }
                    ?: Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PocketTheme.colors.text3,
            )
        }

        // Bar
        val barFraction = run {
            if (max > BigDecimal.ZERO) {
                return@run group.total.divide(max, 6, java.math.RoundingMode.HALF_UP).toFloat()
            }
            0f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(PocketTheme.colors.surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Color(group.color)),
            )
        }

        // Show prev value row
        if (showPrev) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = prevLabel,
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
                val prevTotal = group.prevTotal
                if (prevTotal != null) {
                    Text(
                        text = formatter.format(prevTotal),
                        style = PocketTheme.typography.monoSm,
                        color = PocketTheme.colors.text2,
                    )
                }
                if (prevTotal == null) {
                    Text(
                        text = "sem registro",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }

        // Drill-down tags
        if (open && group.tags.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketTheme.colors.bg2)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.tags.forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(PocketTheme.shapes.pill)
                                .background(Color(group.color)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text2,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatter.format(tag.total),
                            style = PocketTheme.typography.monoSm,
                            color = PocketTheme.colors.text2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeltaBadge(
    delta: Float?,
    invert: Boolean,
) {
    if (delta == null) {
        Box(
            modifier = Modifier
                .clip(PocketTheme.shapes.pill)
                .background(PocketTheme.colors.accentBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "novo",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.accent,
            )
        }
        return
    }

    if (abs(delta) < 0.005f) {
        Box(
            modifier = Modifier
                .clip(PocketTheme.shapes.pill)
                .background(PocketTheme.colors.surface2)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "—",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text3,
            )
        }
        return
    }

    val up = delta > 0
    // despesas: up = bad; receitas: up = good (invert flag)
    val good = up.takeIf { invert } ?: !up
    val badgeColor = PocketTheme.colors.income.takeIf { good } ?: PocketTheme.colors.expense
    val badgeBg = PocketTheme.colors.incomeBg.takeIf { good } ?: PocketTheme.colors.expenseBg
    val arrow = "▲".takeIf { up } ?: "▼"
    val pctInt = (abs(delta) * 100).toInt()

    Box(
        modifier = Modifier
            .clip(PocketTheme.shapes.pill)
            .background(badgeBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$arrow$pctInt%",
            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
            color = badgeColor,
        )
    }
}
