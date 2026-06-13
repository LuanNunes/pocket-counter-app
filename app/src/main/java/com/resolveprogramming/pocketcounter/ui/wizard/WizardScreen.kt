package com.resolveprogramming.pocketcounter.ui.wizard

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepAmount
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepPayment
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepSource
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

    if (state.isLoading) {
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
            paymentSources = state.paymentSources,
            sources = state.filteredSources,
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
            .background(PocketTheme.colors.bg),
    ) {
        WizardTopBar(
            step = state.step,
            onBack = {
                if (state.step == WizardStep.TYPE) onDismiss()
                else viewModel.previousStep()
            },
            onIgnore = onDismiss,
        )

        WizardProgressBar(step = state.step)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            if (state.tokens.isNotEmpty()) {
                SourceTextCard(
                    notification = notification,
                    tokens = state.tokens,
                    selectedTokenIndex = state.selectedTokenIndex,
                    onTokenTap = { viewModel.selectToken(it) },
                )

                if (state.selectedTokenIndex != null) {
                    Spacer(Modifier.height(8.dp))
                    TokenLabelPicker(
                        token = state.tokens[state.selectedTokenIndex!!],
                        onRoleSelected = { role ->
                            viewModel.assignTokenRole(state.selectedTokenIndex!!, role)
                        },
                        onRemoveRole = { viewModel.removeTokenRole(state.selectedTokenIndex!!) },
                    )
                }

                Spacer(Modifier.height(20.dp))
            }

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    if (targetState.index > initialState.index) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
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
                        )
                    }

                    WizardStep.PAYMENT -> StepPayment(
                        paymentSources = state.paymentSources,
                        selectedId = state.draft.idPaymentSource,
                        suggestedId = notification.suggestions.idPaymentSource,
                        paymentHint = notification.parsed.paymentHint,
                        onSelect = viewModel::selectPaymentSource,
                    )

                    WizardStep.SOURCE -> StepSource(
                        sources = state.filteredSources,
                        selectedId = state.draft.idSource,
                        suggestedId = notification.suggestions.idSource,
                        merchantRaw = notification.parsed.merchantRaw,
                        searchQuery = state.sourceSearchQuery,
                        onSearchChange = viewModel::updateSourceSearch,
                        onSelect = viewModel::selectSource,
                        onCreateNew = viewModel::createSource,
                    )

                    WizardStep.TAGS -> StepTags(
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

        WizardFooter(
            step = state.step,
            draft = state.draft,
            isSaving = state.isSaving,
            onBack = {
                if (state.step == WizardStep.TYPE) onDismiss()
                else viewModel.previousStep()
            },
            onNext = {
                if (state.step == WizardStep.TAGS) viewModel.save()
                else viewModel.nextStep()
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
            Text("⚠", style = PocketTheme.typography.screenH1, color = PocketTheme.colors.text3)
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
                Text(
                    text = "←",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
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
            val color = when {
                s.index < step.index -> PocketTheme.colors.text
                s.index == step.index -> PocketTheme.colors.accent
                else -> PocketTheme.colors.line
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
    draft: com.resolveprogramming.pocketcounter.domain.model.WizardDraft,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val canAdvance = when (step) {
        WizardStep.TYPE -> draft.isStep1Valid()
        WizardStep.AMOUNT -> draft.isStep2Valid()
        WizardStep.PAYMENT -> draft.isStep3Valid()
        WizardStep.SOURCE -> draft.isStep4Valid()
        WizardStep.TAGS -> true
    }

    val nextLabel = when (step) {
        WizardStep.TAGS -> {
            if (draft.tagIds.isEmpty()) "Salvar sem tags" else "Salvar transação"
        }
        else -> "Continuar"
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
            fillMaxWidth = true,
            modifier = Modifier.weight(1f),
        )
        PocketButton(
            text = if (isSaving) "Salvando..." else nextLabel,
            onClick = onNext,
            variant = PocketButtonVariant.PRIMARY,
            enabled = canAdvance && !isSaving,
            fillMaxWidth = true,
            modifier = Modifier.weight(1.5f),
        )
    }
}
