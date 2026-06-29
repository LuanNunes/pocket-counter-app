package com.resolveprogramming.pocketcounter.ui.assistente

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.AssistantMessage
import com.resolveprogramming.pocketcounter.domain.model.AssistantMessageStatus
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

private val suggestions = listOf(
    "Quanto gastei este mês?",
    "Quais minhas maiores despesas?",
    "Como está meu saldo?",
    "Em que categoria gasto mais?",
)

@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(state.items.size, state.loadingPhase) {
        if (state.items.isNotEmpty()) {
            val target = state.items.size - 1
            if (reducedMotion) listState.scrollToItem(target)
            if (!reducedMotion) listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SquareIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Assistente", style = PocketTheme.typography.screenH1, color = PocketTheme.colors.text)
                state.remaining?.let { r ->
                    Text(
                        "1 pergunta restante hoje".takeIf { r == 1 } ?: "$r perguntas restantes hoje",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }

        if (state.unavailable) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PocketTheme.colors.text3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Assistente indisponível", style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = PocketTheme.colors.text)
                    Spacer(Modifier.height(4.dp))
                    Text("O assistente está temporariamente fora do ar. Tente novamente mais tarde.", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.items.isEmpty()) {
                item { EmptyState(onSuggestion = viewModel::sendSuggestion) }
            }
            items(state.items.size, key = { state.items[it].id }) { i ->
                MessageItem(state.items[i], loadingPhase = state.loadingPhase, onRetry = viewModel::retry)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Composer(
            input = state.input,
            canSend = state.canSend,
            busy = state.busy,
            quotaExhausted = state.remaining == 0,
            inlineError = state.inlineError,
            onInput = viewModel::updateInput,
            onSend = viewModel::send,
        )
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = PocketTheme.colors.accent,
        )
        Spacer(Modifier.height(8.dp))
        Text("Pergunte sobre suas finanças", style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = PocketTheme.colors.text)
        Spacer(Modifier.height(4.dp))
        Text("Respostas baseadas nas suas transações.", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        Spacer(Modifier.height(16.dp))
        suggestions.forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 4.dp)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                    .clickable { onSuggestion(s) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s, style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2)
            }
        }
    }
}

@Composable
private fun MessageItem(message: AssistantMessage, loadingPhase: Int, onRetry: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // User bubble (right)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(message.question, style = PocketTheme.typography.body, color = PocketTheme.colors.text)
            }
        }
        // Assistant card (left)
        when (message.status) {
            AssistantMessageStatus.LOADING -> PocketCard(modifier = Modifier.fillMaxWidth()) {
                val phase = run {
                    when (loadingPhase) {
                        0 -> return@run "Consultando seus dados…"
                        1 -> return@run "Analisando os resultados…"
                    }
                    "Escrevendo a resposta…"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spinner()
                    Text(phase, style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text3, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            AssistantMessageStatus.OK -> PocketCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column {
                    message.answer?.let { MarkdownAnswer(it.markdown) }
                    message.answer?.let {
                        Spacer(Modifier.height(8.dp))
                        val secs = String.format(java.util.Locale("pt", "BR"), "%.1f", it.elapsedMs / 1000.0)
                        Text("${secs}s", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
                    }
                }
            }
            AssistantMessageStatus.ERROR -> PocketCard(modifier = Modifier.fillMaxWidth(), borderColor = PocketTheme.colors.line) {
                Column {
                    Text("Não consegui responder agora.", style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2)
                    Spacer(Modifier.height(8.dp))
                    Text("Tentar novamente", style = PocketTheme.typography.bodySm, color = PocketTheme.colors.accent, modifier = Modifier.clickable { onRetry(message.id) })
                }
            }
            AssistantMessageStatus.LIMIT -> PocketCard(modifier = Modifier.fillMaxWidth()) {
                Text("Limite diário atingido. Suas perguntas renovam à meia-noite.", style = PocketTheme.typography.bodySm, color = PocketTheme.colors.warn)
            }
        }
    }
}

/**
 * Indeterminate spinner. Per the design spec, reduced-motion SLOWS the spin (to ~2.4s) rather
 * than removing it, so the loading state still reads as "working".
 */
@Composable
private fun Spinner() {
    val reducedMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "assistant-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400.takeIf { reducedMotion } ?: 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "assistant-spin",
    )
    val color = PocketTheme.colors.accent
    Canvas(modifier = Modifier.size(18.dp)) {
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    canSend: Boolean,
    busy: Boolean,
    quotaExhausted: Boolean,
    inlineError: String?,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PocketTheme.colors.surface).padding(12.dp)) {
        if (inlineError != null) {
            Text(inlineError, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.expense)
            Spacer(Modifier.height(6.dp))
        }
        if (inlineError == null && quotaExhausted) {
            Text("Limite diário atingido — renova à meia-noite", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.expense)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, PocketTheme.colors.line2, PocketTheme.shapes.chip)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    enabled = !busy && !quotaExhausted,
                    textStyle = PocketTheme.typography.body.copy(color = PocketTheme.colors.text),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (canSend) onSend() }),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "Analisando sua pergunta…".takeIf { busy } ?: "Pergunte algo…",
                                style = PocketTheme.typography.body,
                                color = PocketTheme.colors.text3,
                            )
                        }
                        inner()
                    },
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(1f.takeIf { canSend } ?: 0.35f)
                    .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    modifier = Modifier.size(20.dp),
                    tint = PocketTheme.colors.accentInk,
                )
            }
        }
        // Reveal the counter only as the user nears the cap (last ~60 chars).
        val counterRevealAt = ASSISTANT_MAX_CHARS - 60
        if (input.length >= counterRevealAt) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${input.length}/$ASSISTANT_MAX_CHARS",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.warn.takeIf { input.length >= ASSISTANT_MAX_CHARS } ?: PocketTheme.colors.text3,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End),
            )
        }
    }
}
