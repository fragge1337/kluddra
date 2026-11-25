package app.kluddra.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kluddra.android.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onServerUrlChange: (String) -> Unit,
    onProbeServer: () -> Unit,
    onConfirmLocalHttp: () -> Unit,
    onDismissLocalHttp: () -> Unit,
    onSetupCodeChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitCredentials: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val resolvedError = when (state.errorCode) {
        "INVALID_URL" -> stringResource(R.string.error_invalid_url)
        "PUBLIC_HTTP_BLOCKED" -> stringResource(R.string.error_public_http_blocked)
        else -> state.errorMessage
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialShapes.Clover4Leaf.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }

                when (state.step) {
                    OnboardingStep.ENTER_SERVER -> {
                        Text(
                            text = stringResource(R.string.onboarding_headline),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.onboarding_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.serverUrlInput,
                            onValueChange = onServerUrlChange,
                            label = { Text(stringResource(R.string.server_url_label)) },
                            placeholder = { Text(stringResource(R.string.server_url_hint)) },
                            singleLine = true,
                            isError = resolvedError != null,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onProbeServer() }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (resolvedError != null) {
                            Text(
                                text = resolvedError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = onProbeServer,
                            enabled = !state.isLoading && state.serverUrlInput.isNotBlank(),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (state.isLoading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(R.string.action_connect))
                            }
                        }
                    }

                    OnboardingStep.INITIAL_SETUP, OnboardingStep.SIGN_IN -> {
                        val isSetup = state.step == OnboardingStep.INITIAL_SETUP
                        Text(
                            text = stringResource(if (isSetup) R.string.setup_heading else R.string.login_heading),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSetup) {
                                stringResource(R.string.setup_subtitle)
                            } else {
                                stringResource(R.string.login_subtitle, state.validatedServerUrl)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isSetup) {
                            OutlinedTextField(
                                value = state.setupCode,
                                onValueChange = onSetupCodeChange,
                                label = { Text(stringResource(R.string.setup_code_label)) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            label = { Text(stringResource(R.string.username_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = { Text(stringResource(R.string.password_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = null
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmitCredentials() }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (resolvedError != null) {
                            Text(
                                text = resolvedError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = onSubmitCredentials,
                            enabled = !state.isLoading && state.username.isNotBlank() && state.password.isNotBlank() &&
                                (!isSetup || state.setupCode.isNotBlank()),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (state.isLoading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(if (isSetup) R.string.action_create_account else R.string.action_sign_in))
                            }
                        }

                        TextButton(
                            onClick = onChangeServer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.action_change_server))
                        }
                    }
                }
            }
        }
    }

    state.pendingLocalHttpHost?.let { host ->
        AlertDialog(
            onDismissRequest = onDismissLocalHttp,
            title = { Text(stringResource(R.string.local_http_warning_title)) },
            text = { Text(stringResource(R.string.local_http_warning_body, host)) },
            confirmButton = {
                TextButton(onClick = onConfirmLocalHttp) {
                    Text(stringResource(R.string.local_http_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLocalHttp) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
