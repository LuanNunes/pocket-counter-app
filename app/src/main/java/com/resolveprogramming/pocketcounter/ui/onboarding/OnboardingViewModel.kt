package com.resolveprogramming.pocketcounter.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.CaptureSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val notificationAccessGranted: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val captureSettingsStore: CaptureSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onGrantsChanged(notificationAccessGranted: Boolean) {
        _state.update { it.copy(notificationAccessGranted = notificationAccessGranted) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            captureSettingsStore.setOnboardingSeen(true)
        }
    }
}
