package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.data.repository.IntentReadFailure
import com.resolveprogramming.pocketcounter.ui.components.FormErrorNote
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.ctaSpinner
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.dashedBorder
import kotlinx.coroutines.delay

private const val COUNTER_FROM = 450
private const val COUNTER_WARN_FROM = 490
private const val AUTOFOCUS_DELAY_MILLIS = 400L

/**
 * The four stages of a quick lançamento. Hosted by the screen that opens it, like every other sheet
 * here — a toast would render behind its own scrim, so every message inside is inline.
 */
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onFlash: (String) -> Unit,
    onManualEntry: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val close = {
        state.savedTransactionId?.let(onFlash)
        viewModel.reset()
        onDismiss()
    }
    val escape = {
        viewModel.escapeToManualEntry()
        viewModel.reset()
        onManualEntry()
    }

    // The create-tag form is the innermost scope, so back closes it before it walks the questions.
    BackHandler(enabled = state.isCreatingTag, onBack = viewModel::closeNewTag)
    BackHandler(
        enabled = !state.isCreatingTag && state.stage == QuickAddStage.ASK,
        onBack = viewModel::backAsk,
    )

    PocketBottomSheet(onDismissRequest = close) {
        Column(modifier = Modifier.fillMaxHeight(0.88f).imePadding()) {
            SheetHeader(onClose = close)
            Spacer(Modifier.height(16.dp))
            when (state.stage) {
                QuickAddStage.INPUT -> QuickAddInputStage(
                    state = state,
                    onText = viewModel::setText,
                    onSubmit = viewModel::submitSentence,
                    onManualEntry = escape,
                )

                QuickAddStage.ASK -> QuickAddAskStage(
                    state = state,
                    onAmount = viewModel::setAmount,
                    onName = viewModel::setName,
                    onCard = viewModel::answerCard,
                    onSkipCard = viewModel::skipCard,
                    onType = viewModel::answerType,
                    onConfirm = viewModel::confirmAsk,
                    onEditSentence = viewModel::editSentence,
                )

                QuickAddStage.PREVIEW -> QuickAddPreviewStage(
                    state = state,
                    callbacks = QuickAddPreviewCallbacks(
                        onToggleRow = viewModel::toggleRow,
                        onAmount = viewModel::setAmount,
                        onName = viewModel::setName,
                        onDate = viewModel::setDate,
                        onType = viewModel::selectType,
                        onPaymentMethod = viewModel::setPaymentMethod,
                        onPaymentStatus = viewModel::setPaymentStatus,
                        onCard = viewModel::selectCard,
                        onToggleTag = viewModel::toggleTag,
                        onOpenNewTag = viewModel::openNewTag,
                        onCloseNewTag = viewModel::closeNewTag,
                        onCreateTag = viewModel::createAndApplyTag,
                        onTeachEnabled = viewModel::setTeachEnabled,
                        onTeachPattern = viewModel::setTeachPattern,
                        onSave = viewModel::save,
                        onSaveAnyway = viewModel::saveAnyway,
                        onDismissDuplicate = viewModel::dismissDuplicate,
                        onEditSentence = viewModel::editSentence,
                        onRetryLookups = viewModel::retryLookups,
                    ),
                )

                QuickAddStage.SAVED -> QuickAddSavedStage(state = state, onDone = close)
            }
        }
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(PocketTheme.colors.accentBg, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = PocketTheme.colors.accent,
            )
        }
        Text(
            text = "Lançamento rápido",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // The 34dp tile is the visual; the touch target is this 48dp wrapper.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fechar",
                    modifier = Modifier.size(18.dp),
                    tint = PocketTheme.colors.text2,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.QuickAddInputStage(
    state: QuickAddUiState,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onManualEntry: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Requested during the sheet's enter animation the focus is dropped, so wait for it to settle.
    LaunchedEffect(Unit) {
        delay(AUTOFOCUS_DELAY_MILLIS)
        // The field is gone if the stage changed inside the delay: nothing to focus, nothing to say.
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SentenceField(state = state, onText = onText, focusRequester = focusRequester)
        if (state.text.length >= COUNTER_FROM) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.text.length}/$QUICK_ADD_MAX_CHARS",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.warn.takeIf { state.text.length >= COUNTER_WARN_FROM }
                    ?: PocketTheme.colors.text3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }

        Spacer(Modifier.height(12.dp))
        DictationRow()

        state.readFailure?.let { failure ->
            Spacer(Modifier.height(16.dp))
            FormErrorNote(readFailureCopy(failure, state.retrySeconds))
            Spacer(Modifier.height(8.dp))
            PocketButton(
                text = "Lançar manualmente",
                onClick = onManualEntry,
                variant = PocketButtonVariant.SOFT,
                fillMaxWidth = true,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PocketButton(
            text = "Lendo a frase...".takeIf { state.isReading } ?: "Continuar",
            onClick = onSubmit,
            enabled = state.canSubmitSentence,
            fillMaxWidth = true,
            leading = ctaSpinner().takeIf { state.isReading },
        )
    }
}

@Composable
private fun SentenceField(
    state: QuickAddUiState,
    onText: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val sentenceStyle = PocketTheme.typography.body.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp)
    BasicTextField(
        value = state.text,
        onValueChange = onText,
        readOnly = state.isReading,
        minLines = 2,
        maxLines = 6,
        textStyle = sentenceStyle.copy(color = PocketTheme.colors.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        cursorBrush = SolidColor(PocketTheme.colors.accent),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.field)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.field)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        text = "Ex.: paguei 250 em uma consulta do cachorro",
                        style = sentenceStyle,
                        color = PocketTheme.colors.text3,
                    )
                }
                inner()
            }
        },
    )
}

/** Nothing here is clickable, so `disabled()` is the truth — unlike on the Home field. */
@Composable
private fun DictationRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(PocketTheme.colors.line2, 15.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Ditar por voz. Em breve — por enquanto, escreva a frase."
                disabled()
            },
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .alpha(0.7f)
                .background(PocketTheme.colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = PocketTheme.colors.text3,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Ditar por voz",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Text(
                text = "Em breve — por enquanto, escreva a frase.",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }
    }
}

@Composable
private fun ColumnScope.QuickAddSavedStage(state: QuickAddUiState, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(PocketTheme.colors.income, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Lançamento criado",
                modifier = Modifier.size(22.dp),
                // White on the dark theme's income is 2.32:1; accentInk clears the graphical 3:1.
                tint = PocketTheme.colors.accentInk,
            )
        }
        Spacer(Modifier.height(14.dp))
        state.draft.amount?.let { amount ->
            AmountText(
                amount = amount.signedFor(state.draft.type),
                type = state.draft.type,
                showSign = true,
                style = PocketTheme.typography.monoBalance.copy(
                    fontSize = 30.sp,
                    letterSpacing = (-0.03f).em,
                ),
                autoSize = true,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.draft.name.orEmpty(),
            style = PocketTheme.typography.body.copy(
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = PocketTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${typeWord(state.draft.type)} lançada · " +
                statusWord(state.draft.statusPayment).lowercase(),
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )
        state.teachNote?.let { note ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = note,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text2,
                textAlign = TextAlign.Center,
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PocketButton(text = "Concluir", onClick = onDone, fillMaxWidth = true)
    }
}

internal fun readFailureCopy(failure: IntentReadFailure, retrySeconds: Int?): String = when (failure) {
    IntentReadFailure.Offline ->
        "Sem conexão. Você pode lançar manualmente — a frase vai no campo Descrição."

    IntentReadFailure.Timeout -> "A leitura demorou demais. Tente de novo ou lance manualmente."
    IntentReadFailure.NotUnderstood -> "Não entendi essa frase. Tente reescrever ou lance manualmente."
    is IntentReadFailure.RateLimited -> rateLimitedCopy(retrySeconds)
    IntentReadFailure.ServerError, IntentReadFailure.Unknown ->
        "Não foi possível ler a frase agora. Tente de novo ou lance manualmente."
}

private fun rateLimitedCopy(retrySeconds: Int?): String {
    val seconds = retrySeconds ?: return "Tente de novo."
    return "Muitos lançamentos seguidos. Tente de novo em ${seconds}s."
}
