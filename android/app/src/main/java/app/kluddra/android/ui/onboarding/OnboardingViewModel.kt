package app.kluddra.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kluddra.android.data.remote.ApiException
import app.kluddra.android.data.remote.UrlSecurityPolicy
import app.kluddra.android.data.remote.UrlValidationResult
import app.kluddra.android.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    ENTER_SERVER,
    INITIAL_SETUP,
    SIGN_IN
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ENTER_SERVER,
    val serverUrlInput: String = "",
    val validatedServerUrl: String = "",
    val allowLocalHttp: Boolean = false,
    val pendingLocalHttpHost: String? = null,
    val setupCode: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

class OnboardingViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrlInput = value, errorCode = null, errorMessage = null) }
    }

    fun onSetupCodeChange(value: String) {
        _uiState.update { it.copy(setupCode = value, errorCode = null, errorMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorCode = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorCode = null, errorMessage = null) }
    }

    fun dismissLocalHttpConsent() {
        _uiState.update { it.copy(pendingLocalHttpHost = null) }
    }

    fun confirmLocalHttpConsent() {
        _uiState.update { it.copy(pendingLocalHttpHost = null, allowLocalHttp = true) }
        probeServer(forceAllowLocalHttp = true)
    }

    fun goBackToServerStep() {
        _uiState.update {
            it.copy(
                step = OnboardingStep.ENTER_SERVER,
                password = "",
                setupCode = "",
                errorCode = null,
                errorMessage = null
            )
        }
    }

    fun probeServer(forceAllowLocalHttp: Boolean = false) {
        val state = _uiState.value
        val allowLocal = forceAllowLocalHttp || state.allowLocalHttp

        when (val result = UrlSecurityPolicy.evaluate(state.serverUrlInput, allowLocal)) {
            UrlValidationResult.Invalid -> {
                _uiState.update { it.copy(errorCode = "INVALID_URL") }
            }
            UrlValidationResult.PublicHttpBlocked -> {
                _uiState.update { it.copy(errorCode = "PUBLIC_HTTP_BLOCKED") }
            }
            is UrlValidationResult.RequiresLocalHttpConsent -> {
                _uiState.update { it.copy(pendingLocalHttpHost = result.host) }
            }
            is UrlValidationResult.Valid -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, errorCode = null, errorMessage = null) }
                    try {
                        val info = authRepository.probeServer(result.normalizedUrl, result.isLocalHttp)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                validatedServerUrl = result.normalizedUrl,
                                allowLocalHttp = result.isLocalHttp,
                                step = if (info.setupRequired) OnboardingStep.INITIAL_SETUP else OnboardingStep.SIGN_IN
                            )
                        }
                    } catch (e: ApiException) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.localizedMessage ?: "Could not reach server"
                            )
                        }
                    }
                }
            }
        }
    }

    fun submitCredentials() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null, errorMessage = null) }
            try {
                if (state.step == OnboardingStep.INITIAL_SETUP) {
                    authRepository.completeSetup(
                        serverUrl = state.validatedServerUrl,
                        allowLocalHttp = state.allowLocalHttp,
                        setupCode = state.setupCode,
                        username = state.username,
                        password = state.password
                    )
                } else {
                    authRepository.login(
                        serverUrl = state.validatedServerUrl,
                        allowLocalHttp = state.allowLocalHttp,
                        username = state.username,
                        password = state.password
                    )
                }
                _uiState.update { it.copy(isLoading = false, password = "") }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Authentication failed"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(authRepository) as T
            }
    }
}
