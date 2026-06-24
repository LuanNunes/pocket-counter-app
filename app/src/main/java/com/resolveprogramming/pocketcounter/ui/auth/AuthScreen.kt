package com.resolveprogramming.pocketcounter.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.R
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(100.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(PocketTheme.colors.accent, PocketTheme.shapes.card),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                style = PocketTheme.typography.display,
                color = PocketTheme.colors.accentInk,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "PocketCounter",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = state.isRegisterMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "subtitle",
        ) { isRegister ->
            Text(
                text = if (isRegister) "Crie sua conta para começar" else "Faça login para continuar",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.height(40.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PocketTheme.colors.accent,
            unfocusedBorderColor = PocketTheme.colors.line,
            focusedLabelColor = PocketTheme.colors.accent,
            cursorColor = PocketTheme.colors.accent,
        )

        AnimatedContent(
            targetState = state.isRegisterMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "name-field",
        ) { isRegister ->
            if (isRegister) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = PocketTheme.typography.body,
                    colors = fieldColors,
                    shape = PocketTheme.shapes.chip,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
            } else {
                Spacer(Modifier)
            }
        }

        if (state.isRegisterMode) Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = PocketTheme.typography.body,
            colors = fieldColors,
            shape = PocketTheme.shapes.chip,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Senha") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = PocketTheme.typography.body,
            colors = fieldColors,
            shape = PocketTheme.shapes.chip,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
            ),
        )

        if (state.errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.errorMessage!!,
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.expense,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (state.isLoading) PocketTheme.colors.accent.copy(alpha = 0.6f)
                    else PocketTheme.colors.accent,
                    PocketTheme.shapes.chip,
                )
                .then(
                    if (state.isLoading) Modifier
                    else Modifier.clickable { viewModel.submit() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = PocketTheme.colors.accentInk,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (state.isRegisterMode) "Criar conta" else "Entrar",
                    style = PocketTheme.typography.button,
                    color = PocketTheme.colors.accentInk,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(PocketTheme.colors.line),
            )
            Text(
                text = "ou",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(PocketTheme.colors.line),
            )
        }

        Spacer(Modifier.height(20.dp))

        val googleEnabled = !state.isLoading && !state.isGoogleLoading
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                .then(
                    if (googleEnabled) Modifier.clickable { viewModel.signInWithGoogle(context) }
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isGoogleLoading) {
                CircularProgressIndicator(
                    color = PocketTheme.colors.text,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continuar com Google",
                        style = PocketTheme.typography.button,
                        color = PocketTheme.colors.text,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val toggleText = if (state.isRegisterMode) {
            "Já tem conta? Entrar"
        } else {
            "Não tem conta? Criar conta"
        }

        Text(
            text = toggleText,
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.accent,
            modifier = Modifier
                .clickable { viewModel.toggleMode() }
                .padding(vertical = 8.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}
