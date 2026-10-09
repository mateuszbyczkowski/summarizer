package com.summarizer.app.ui.screens.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.domain.model.PinVerification
import com.summarizer.app.util.BiometricHelper
import kotlinx.coroutines.launch

@Composable
fun PinLockScreen(
    onUnlocked: () -> Unit,
    viewModel: PinLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val canUseBiometric = remember(context) { BiometricHelper.canUseBiometric(context) }
    val showBiometricButton = isBiometricEnabled && canUseBiometric

    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
    var biometricError by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-trigger biometric on screen load if enabled
    LaunchedEffect(showBiometricButton) {
        val activity = context.findFragmentActivity()
        if (showBiometricButton && activity != null) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Unlock App",
                subtitle = "Use biometric to unlock",
                onSuccess = { onUnlocked() },
                onError = { error ->
                    biometricError = error
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your PIN to unlock the app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = shakeOffset.value.dp),
            onValueChange = {
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    pin = it
                    errorMessage = null

                    // Auto-verify when 6 digits entered
                    if (it.length == 6) {
                        viewModel.verifyPin(it) { result ->
                            when (result) {
                                PinVerification.Success -> onUnlocked()
                                is PinVerification.Incorrect -> {
                                    errorMessage = "Incorrect PIN. Try again."
                                    pin = ""
                                    coroutineScope.launch { shake(shakeOffset) }
                                }
                                is PinVerification.LockedOut -> {
                                    val seconds = (result.remainingMillis + 999) / 1000
                                    errorMessage = "Too many attempts. Try again in ${seconds}s."
                                    pin = ""
                                }
                            }
                        }
                    }
                }
            },
            enabled = !isVerifying,
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = errorMessage != null
        )

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (biometricError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = biometricError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter 6 digits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // Biometric button (if enabled and available)
        if (showBiometricButton) {
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    val activity = context.findFragmentActivity()
                    if (activity == null) {
                        biometricError = "Biometric unlock is unavailable"
                    } else {
                        BiometricHelper.showBiometricPrompt(
                            activity = activity,
                            title = "Unlock App",
                            subtitle = "Use biometric to unlock",
                            onSuccess = { onUnlocked() },
                            onError = { error ->
                                biometricError = error
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use Biometric")
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private suspend fun shake(offset: Animatable<Float, *>) {
    repeat(3) {
        offset.animateTo(15f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh))
        offset.animateTo(-15f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh))
    }
    offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
}
