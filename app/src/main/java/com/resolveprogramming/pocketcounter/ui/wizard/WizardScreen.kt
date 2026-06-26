package com.resolveprogramming.pocketcounter.ui.wizard

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepAmount
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepPayment
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepType
import java.time.LocalDate

@Composable
fun WizardScreen(
    onDismiss: () -> Unit,
    onBackToApp: () -> Unit,
    viewModel: WizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Full-screen spinner only on the very first open, when there's nothing to keep on screen.
    // Switching between queued items keeps the current item visible (see isSwitching below).
    if (state.isLoading && state.notification == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    if (state.pendingConfirmed) {
        PendingConfirmedScreen(onBackToApp = onBackToApp)
        return
    }

    if (state.isSuccess) {
        SuccessScreen(
            draft = state.draft,
            cards = state.cards,
            allTags = state.allTags,
            contexts = state.contexts,
            onViewTransaction = onDismiss,
            onBackToApp = onBackToApp,
        )
        return
    }

    val notification = state.notification ?: run {
        // notification == null here only when the initial load failed (loading already returned
        // above) — render a recoverable error instead of a blank screen.
        WizardLoadError(message = state.error, onDismiss = onDismiss)
        return
    }

    if (state.isConfirmingPending) {
        PendingConfirmScreen(
            notification = notification,
            isSaving = state.isSaving,
            onConfirm = viewModel::confirmPending,
            onDismiss = onDismiss,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            // Edge-to-edge is on; without this the top bar (Ignorar) hides under the status bar and
            // the footer actions (Voltar/Continuar/Salvar) hide under the system navigation bar.
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        WizardTopBar(
            step = state.step,
            onBack = onDismiss,
            onIgnore = { viewModel.ignore(onDone = onBackToApp) },
        )

        // Slim, unobtrusive loading hint while the next queued item resolves in place.
        if (state.isSwitching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = PocketTheme.colors.accent,
                trackColor = Color.Transparent,
            )
        }

        val queueIndex = state.queue.indexOf(notification.id)
        if (state.queue.size >= 2 && queueIndex >= 0) {
            WizardQueueStrip(
                position = queueIndex + 1,
                total = state.queue.size,
                enabled = !state.isSwitching,
                onPrevItem = viewModel::skipToPrevious,
                onNextItem = viewModel::skipToNext,
            )
        }

        WizardProgressBar(step = state.step)

        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(0.6f.takeIf { state.isSwitching } ?: 1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Crossfade(targetState = notification.id, label = "wizard_item") { _ ->
                Column {
                    if (state.tokens.isNotEmpty()) {
                        val range = state.selectionRange
                        SourceTextCard(
                            notification = notification,
                            tokens = state.tokens,
                            selectionStart = range?.first,
                            selectionEnd = range?.last,
                            onTokenTap = viewModel::tapToken,
                        )

                        if (range != null) {
                            Spacer(Modifier.height(8.dp))
                            val preview = state.tokens.subList(range.first, range.last + 1)
                                .joinToString(" ") { it.text }
                            TokenLabelPicker(
                                preview = preview,
                                currentRole = state.tokens[range.first].role,
                                onRoleSelected = viewModel::assignRoleToSelection,
                                onRemoveRole = viewModel::removeRoleFromSelection,
                                onClear = viewModel::clearSelection,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                    }

                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            run {
                                if (targetState.index > initialState.index) {
                                    return@run slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                                }
                                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                            }
                        },
                        label = "wizard_step",
                    ) { step ->
                        when (step) {
                            WizardStep.TYPE -> StepType(
                                suggestedType = notification.parsed.type,
                                selectedType = state.draft.type,
                                onSelect = viewModel::selectType,
                            )

                            WizardStep.AMOUNT -> {
                                val context = LocalContext.current
                                StepAmount(
                                    amount = state.draft.amount,
                                    date = state.draft.date,
                                    statusPayment = state.draft.statusPayment,
                                    hasInstallments = notification.parsed.installments != null,
                                    installmentsEnabled = state.draft.installments != null,
                                    installmentCount = notification.parsed.installments,
                                    installmentValue = notification.parsed.installmentValue,
                                    isFixo = state.draft.isFixo,
                                    recurrenceDay = state.draft.recurrenceDay,
                                    name = state.draft.name,
                                    type = state.draft.type,
                                    onNameChange = viewModel::updateName,
                                    onAmountChange = viewModel::updateAmount,
                                    onDateTap = {
                                        val current = state.draft.date ?: LocalDate.now()
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                viewModel.updateDate(LocalDate.of(year, month + 1, day))
                                            },
                                            current.year,
                                            current.monthValue - 1,
                                            current.dayOfMonth,
                                        ).show()
                                    },
                                    onStatusChange = viewModel::updateStatusPayment,
                                    onToggleInstallments = viewModel::toggleInstallments,
                                    onToggleFixo = viewModel::toggleFixo,
                                    onRecurrenceDayChange = viewModel::updateRecurrenceDay,
                                )
                            }

                            WizardStep.PAYMENT -> StepPayment(
                                type = state.draft.type,
                                cards = state.cards,
                                selectedMethod = state.draft.paymentMethod,
                                selectedCardId = state.draft.cardId,
                                onSelectMethod = viewModel::selectPaymentMethod,
                                onSelectCard = viewModel::selectCard,
                            )

                            WizardStep.TAGS -> StepTags(
                                type = state.draft.type ?: TransactionType.EXPENSE,
                                tags = state.allTags,
                                contexts = state.contexts,
                                selectedTagIds = state.draft.tagIds,
                                searchQuery = state.tagSearchQuery,
                                learnRule = state.draft.learnRule,
                                paymentHint = notification.parsed.paymentHint,
                                merchant = state.draft.merchant,
                                onSearchChange = viewModel::updateTagSearch,
                                onToggleTag = viewModel::toggleTag,
                                onToggleLearnRule = viewModel::toggleLearnRule,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        WizardFooter(
            step = state.step,
            draft = state.draft,
            isSaving = state.isSaving,
            enabled = !state.isSwitching,
            onBack = {
                if (state.step == WizardStep.TYPE) onDismiss()
                if (state.step != WizardStep.TYPE) viewModel.previousStep()
            },
            onNext = {
                if (state.step == WizardStep.TAGS) {
                    viewModel.save(onDone = onBackToApp)
                }
                if (state.step != WizardStep.TAGS) viewModel.nextStep()
            },
        )
    }
}

@Composable
private fun WizardLoadError(message: String?, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = PocketTheme.colors.text3,
            )
            Text(
                message ?: "Não foi possível abrir esta notificação.",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text2,
            )
            PocketButton(
                text = "Voltar",
                onClick = onDismiss,
                variant = PocketButtonVariant.SOFT,
            )
        }
    }
}

@Composable
private fun WizardTopBar(
    step: WizardStep,
    onBack: () -> Unit,
    onIgnore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 36dp back square (surface + line border) per the spec header.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    modifier = Modifier.size(24.dp),
                    tint = PocketTheme.colors.text,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Ensinar classificação",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = "${step.label} · ${step.subtitle}",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
        }
        Text(
            text = "Ignorar",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
            modifier = Modifier.clickable(onClick = onIgnore),
        )
    }
}

@Composable
private fun WizardQueueStrip(
    position: Int,
    total: Int,
    enabled: Boolean,
    onPrevItem: () -> Unit,
    onNextItem: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(PocketTheme.shapes.icon)
                .clickable(enabled = enabled, onClick = onPrevItem),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Voltar ao pendente anterior",
                modifier = Modifier.size(24.dp),
                tint = PocketTheme.colors.text2,
            )
        }
        Box(
            modifier = Modifier
                .clearAndSetSemantics { contentDescription = "Pendente $position de $total" }
                .background(PocketTheme.colors.accentBg, PocketTheme.shapes.pill)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Pendente $position de $total",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                color = PocketTheme.colors.accent,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(PocketTheme.shapes.icon)
                .clickable(enabled = enabled, onClick = onNextItem),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Pular para o próximo pendente",
                modifier = Modifier.size(24.dp),
                tint = PocketTheme.colors.text2,
            )
        }
    }
}

@Composable
private fun WizardProgressBar(step: WizardStep) {
    val progress = (step.index + 1) / WizardStep.entries.size.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WizardStep.entries.forEach { s ->
            // Done steps read as --text, the active step as --accent, upcoming as --line.
            val color = run {
                if (s.index < step.index) return@run PocketTheme.colors.text
                if (s.index == step.index) return@run PocketTheme.colors.accent
                PocketTheme.colors.line
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, PocketTheme.shapes.pill),
            )
        }
    }
}

@Composable
private fun WizardFooter(
    step: WizardStep,
    draft: WizardDraft,
    isSaving: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val canAdvance = when (step) {
        WizardStep.TYPE -> draft.isStep1Valid()
        WizardStep.AMOUNT -> draft.isStep2Valid()
        WizardStep.PAYMENT -> draft.isStep3Valid()
        WizardStep.TAGS -> true
    }

    val nextLabel = when (step) {
        WizardStep.TAGS -> "Salvar transação".takeUnless { draft.tagIds.isEmpty() } ?: "Salvar sem tags"
        WizardStep.TYPE -> "Continuar"
        WizardStep.AMOUNT -> "Continuar"
        WizardStep.PAYMENT -> "Continuar"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PocketButton(
            text = "Voltar",
            onClick = onBack,
            variant = PocketButtonVariant.SOFT,
            enabled = enabled,
            fillMaxWidth = true,
            modifier = Modifier.weight(1f),
        )
        PocketButton(
            text = "Salvando...".takeIf { isSaving } ?: nextLabel,
            onClick = onNext,
            variant = PocketButtonVariant.PRIMARY,
            enabled = canAdvance && !isSaving && enabled,
            fillMaxWidth = true,
            modifier = Modifier.weight(1.5f),
        )
    }
}
