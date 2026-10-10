package com.summarizer.app.ui.screens.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.summarizer.app.R
import com.summarizer.app.ui.lock.LockMethod
import com.summarizer.app.util.BiometricHelper
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * The lock screen for [method].
 *
 * @param onUnlocked the app PIN (or a new one) was entered
 * @param onSystemUnlocked the phone's own lock let the user in
 * @param onSystemLockUnavailable the phone has no usable screen lock any more
 */
@Composable
fun AppLockScreen(
    method: LockMethod,
    onUnlocked: () -> Unit,
    onSystemUnlocked: () -> Unit,
    onSystemLockUnavailable: () -> Unit
) {
    // The lock screen lives outside navigation, so its view models belong to the activity. Stable
    // keys keep it at one of each; every time the lock screen is shown it starts a fresh session.
    val session = rememberSaveable { UUID.randomUUID().toString() }
    when (method) {
        LockMethod.SYSTEM -> SystemLockScreen(
            onUnlocked = onSystemUnlocked,
            onUnavailable = onSystemLockUnavailable
        )
        LockMethod.PIN -> {
            val viewModel: PinLockViewModel = hiltViewModel(key = "pin-lock")
            LaunchedEffect(session) { viewModel.startSession() }
            PinLockScreen(onUnlocked = onUnlocked, viewModel = viewModel)
        }
        LockMethod.PIN_SETUP -> {
            val viewModel: PinSetupViewModel = hiltViewModel(key = "pin-setup")
            LaunchedEffect(session) { viewModel.restart() }
            PinSetupScreen(
                onPinSet = onUnlocked,
                subtitle = stringResource(R.string.lock_pin_setup_required_subtitle),
                viewModel = viewModel
            )
        }
    }
}

/** App icon, name and an Unlock button; opens the system prompt once on entry. */
@Composable
private fun SystemLockScreen(
    onUnlocked: () -> Unit,
    onUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun prompt() {
        error = null
        val activity = context.findFragmentActivity() ?: return
        if (!BiometricHelper.canUseSystemLock(context)) {
            error = resources.getString(R.string.lock_system_unavailable)
            onUnavailable()
            return
        }
        BiometricHelper.authenticate(
            activity = activity,
            title = resources.getString(R.string.lock_prompt_title, resources.getString(R.string.app_name)),
            subtitle = null,
            negativeButtonText = null,
            onSuccess = onUnlocked,
            onFailure = { failure ->
                when (failure) {
                    BiometricHelper.Failure.Cancelled -> Unit
                    BiometricHelper.Failure.NoCredential -> {
                        error = resources.getString(R.string.lock_system_unavailable)
                        onUnavailable()
                    }
                    is BiometricHelper.Failure.Error -> error = failure.message
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        // The prompt can only be shown while the activity is in the foreground
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if (!autoPrompted) {
            autoPrompted = true
            prompt()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconBadge(size = 88.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_system_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = ::prompt) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.lock_system_unlock))
            }
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .heightIn(min = 40.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center
            ) {
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
