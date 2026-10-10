package com.summarizer.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.BuildConfig
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.ui.components.SettingsGroup
import com.summarizer.app.ui.components.SettingsRow
import com.summarizer.app.ui.components.SettingsRowTone
import com.summarizer.app.ui.components.SettingsRowTrailing
import com.summarizer.app.ui.components.settingsPageColor
import com.summarizer.app.ui.screens.models.ModelProfiles
import com.summarizer.app.util.PermissionHelper
import com.summarizer.app.util.dateTimeFormats
import java.time.LocalTime

/**
 * Settings as a grouped list (Summaries / Notifications / Privacy & security / About). Each row
 * shows its current value; details live in dialogs and in the model manager. A banner at the top
 * appears only when something keeps the app from working.
 *
 * @param onBackPressed shows a back arrow when set; null when Settings is a top-level destination
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: (() -> Unit)? = null,
    onModelConfigClick: () -> Unit = {},
    onResetComplete: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isResetting by viewModel.isResetting.collectAsStateWithLifecycle()
    val currentOnModelConfigClick by rememberUpdatedState(onModelConfigClick)
    val currentOnResetComplete by rememberUpdatedState(onResetComplete)
    val context = LocalContext.current

    val onEvent: (SettingsEvent) -> Unit = remember(viewModel) {
        { event ->
            when (event) {
                SettingsEvent.ManageModels -> currentOnModelConfigClick()
                SettingsEvent.ResetApp -> viewModel.resetApplication(onComplete = { currentOnResetComplete() })
                else -> viewModel.onEvent(event)
            }
        }
    }

    // Re-checked on resume: the user may come back from system settings or the model manager
    var hasNotificationAccess by remember {
        mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context))
    }
    LifecycleResumeEffect(viewModel) {
        hasNotificationAccess = PermissionHelper.hasNotificationListenerPermission(context)
        viewModel.onEvent(SettingsEvent.Refresh)
        onPauseOrDispose { }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pageColor = settingsPageColor()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = pageColor,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBackPressed != null) {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)

        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is SettingsUiState.Success -> {
                SettingsList(
                    state = state,
                    hasNotificationAccess = hasNotificationAccess,
                    isResetting = isResetting,
                    onEvent = onEvent,
                    modifier = contentModifier
                )
            }

            is SettingsUiState.Error -> {
                Box(
                    modifier = contentModifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(state.messageRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = viewModel::dismissError) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }

    if (isResetting) {
        ResettingDialog()
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState.Success,
    hasNotificationAccess: Boolean,
    isResetting: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val isRestrictiveRom = remember { PermissionHelper.isRestrictiveROM() }
    val romType = remember { PermissionHelper.getROMType() }
    val openNotificationAccess = { PermissionHelper.openNotificationListenerSettings(context) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
    ) {
        StatusBanners(
            hasNotificationAccess = hasNotificationAccess,
            state = state,
            onOpenNotificationAccess = openNotificationAccess,
            onOpenEngine = { onEvent(SettingsEvent.ManageModels) }
        )

        SettingsGroup(label = stringResource(R.string.settings_group_summaries)) {
            SettingsRow(
                title = stringResource(R.string.settings_engine_title),
                icon = Icons.Default.AutoAwesome,
                value = engineValue(state),
                onClick = { onEvent(SettingsEvent.ManageModels) }
            )
            SettingsRow(
                title = stringResource(R.string.settings_daily_title),
                icon = Icons.Default.Schedule,
                value = if (state.autoSummarizationEnabled) {
                    stringResource(R.string.settings_daily_on, formatHour(context, state.autoSummarizationHour))
                } else {
                    stringResource(R.string.settings_value_off)
                },
                onClick = { dialog = SettingsDialog.DailyTime },
                trailing = SettingsRowTrailing.Switch(
                    checked = state.autoSummarizationEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.SetAutoSummarizationEnabled(it)) }
                )
            )
        }

        SettingsGroup(label = stringResource(R.string.settings_group_notifications)) {
            SettingsRow(
                title = stringResource(R.string.settings_smart_title),
                icon = Icons.Default.NotificationsActive,
                value = if (state.smartNotificationsEnabled) {
                    stringResource(
                        R.string.settings_smart_on,
                        stringResource(sensitivityLabel(state.smartNotificationThreshold))
                    )
                } else {
                    stringResource(R.string.settings_value_off)
                },
                onClick = { dialog = SettingsDialog.Sensitivity },
                trailing = SettingsRowTrailing.Switch(
                    checked = state.smartNotificationsEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.SetSmartNotificationsEnabled(it)) }
                )
            )
        }

        SettingsGroup(label = stringResource(R.string.settings_group_privacy)) {
            AppLockRow(
                appLockEnabled = state.appLockEnabled,
                showPinSetup = state.showAppLockPinSetup,
                onEvent = onEvent
            )
            SettingsRow(
                title = stringResource(R.string.settings_retention_row_title),
                icon = Icons.Default.DeleteSweep,
                value = stringResource(
                    R.string.settings_retention_value,
                    pluralStringResource(R.plurals.days, state.dataRetentionDays, state.dataRetentionDays)
                ),
                onClick = { dialog = SettingsDialog.Retention }
            )
            SettingsRow(
                title = stringResource(R.string.settings_access_title),
                icon = Icons.Default.Shield,
                value = stringResource(
                    if (hasNotificationAccess) R.string.settings_access_on else R.string.settings_access_off
                ),
                onClick = openNotificationAccess,
                trailing = SettingsRowTrailing.Status(
                    ok = hasNotificationAccess,
                    contentDescription = stringResource(
                        if (hasNotificationAccess) R.string.settings_access_granted else R.string.settings_access_missing
                    )
                )
            )
            if (isRestrictiveRom) {
                SettingsRow(
                    title = stringResource(R.string.settings_autostart_title, romType),
                    icon = Icons.Default.Autorenew,
                    value = stringResource(R.string.settings_permissions_autostart_info, romType),
                    onClick = { PermissionHelper.openAutoStartSettings(context) }
                )
            }
        }

        SettingsGroup(label = stringResource(R.string.settings_group_about)) {
            SettingsRow(
                title = stringResource(R.string.settings_about_row_title),
                icon = Icons.Default.Info,
                value = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                onClick = { dialog = SettingsDialog.About }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroup(label = null) {
            SettingsRow(
                title = stringResource(R.string.settings_reset_row_title),
                icon = Icons.Default.RestartAlt,
                value = stringResource(R.string.settings_reset_row_value),
                onClick = { dialog = SettingsDialog.Reset },
                trailing = SettingsRowTrailing.None,
                tone = SettingsRowTone.Error,
                enabled = !isResetting
            )
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.DailyTime -> DailyTimeDialog(
            hour = state.autoSummarizationHour,
            onConfirm = { hour ->
                dialog = null
                onEvent(SettingsEvent.SetAutoSummarizationHour(hour))
                if (!state.autoSummarizationEnabled) onEvent(SettingsEvent.SetAutoSummarizationEnabled(true))
            },
            onDismiss = dismiss
        )
        SettingsDialog.Sensitivity -> SensitivityDialog(
            threshold = state.smartNotificationThreshold,
            onConfirm = { threshold ->
                dialog = null
                if (threshold != state.smartNotificationThreshold) {
                    onEvent(SettingsEvent.SetSmartNotificationThreshold(threshold))
                }
                if (!state.smartNotificationsEnabled) onEvent(SettingsEvent.SetSmartNotificationsEnabled(true))
            },
            onDismiss = dismiss
        )
        SettingsDialog.Retention -> RetentionDialog(
            retentionDays = state.dataRetentionDays,
            onSelect = { days ->
                dialog = null
                if (days != state.dataRetentionDays) onEvent(SettingsEvent.SetDataRetentionDays(days))
            },
            onDismiss = dismiss
        )
        SettingsDialog.About -> AboutDialog(onDismiss = dismiss)
        SettingsDialog.Reset -> ResetConfirmationDialog(
            onConfirm = {
                dialog = null
                onEvent(SettingsEvent.ResetApp)
            },
            onDismiss = dismiss
        )
        null -> Unit
    }
}

@Composable
private fun engineValue(state: SettingsUiState.Success): String = when (state.aiProvider) {
    AIProvider.LOCAL -> state.activeModelName
        ?.let { stringResource(R.string.settings_engine_local, ModelProfiles.technicalName(it)) }
        ?: stringResource(R.string.settings_engine_local_none)
    AIProvider.OPENAI -> if (state.hasApiKey) {
        stringResource(R.string.settings_engine_openai, state.selectedModel.displayName)
    } else {
        stringResource(R.string.settings_engine_openai_no_key)
    }
}

/** A full hour in the phone's 12/24-hour style, e.g. "20:00" or "8:00 PM". */
private fun formatHour(context: Context, hour: Int): String = dateTimeFormats(context).time(LocalTime.of(hour, 0))

/** Problems that keep the app from working, each with a fix. Nothing is shown when all is well. */
@Composable
private fun StatusBanners(
    hasNotificationAccess: Boolean,
    state: SettingsUiState.Success,
    onOpenNotificationAccess: () -> Unit,
    onOpenEngine: () -> Unit
) {
    val banners = buildList {
        if (!hasNotificationAccess) {
            add(Triple(R.string.settings_banner_access_title, R.string.settings_banner_access_body, R.string.settings_banner_access_action) to onOpenNotificationAccess)
        }
        if (state.localModelMissing) {
            add(Triple(R.string.settings_banner_model_title, R.string.settings_banner_model_body, R.string.settings_banner_model_action) to onOpenEngine)
        }
        if (state.openAiKeyMissing) {
            add(Triple(R.string.settings_banner_key_title, R.string.settings_banner_key_body, R.string.settings_banner_key_action) to onOpenEngine)
        }
    }
    if (banners.isEmpty()) return

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        banners.forEach { (texts, action) ->
            StatusBanner(
                title = stringResource(texts.first),
                body = stringResource(texts.second),
                actionLabel = stringResource(texts.third),
                onAction = action
            )
        }
    }
}

@Composable
private fun StatusBanner(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(actionLabel)
            }
        }
    }
}
