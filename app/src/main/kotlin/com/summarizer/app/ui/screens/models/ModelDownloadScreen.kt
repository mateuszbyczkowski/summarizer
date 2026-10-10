package com.summarizer.app.ui.screens.models

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.R
import com.summarizer.app.domain.model.AIModel
import com.summarizer.app.domain.model.AIProvider
import com.summarizer.app.domain.model.DownloadStatus
import com.summarizer.app.domain.model.ModelDownloadState
import com.summarizer.app.ui.components.SettingsGroup
import com.summarizer.app.ui.components.SettingsRow
import com.summarizer.app.ui.components.SettingsRowTrailing
import com.summarizer.app.ui.components.formatModelSize
import com.summarizer.app.ui.components.settingsGroupItem
import com.summarizer.app.ui.components.settingsPageColor
import com.summarizer.app.ui.screens.settings.SettingsEvent
import com.summarizer.app.ui.screens.settings.SettingsUiState
import com.summarizer.app.ui.screens.settings.SettingsViewModel
import com.summarizer.app.util.DeviceMemory
import com.summarizer.app.util.StorageHelper
import kotlinx.coroutines.launch

/**
 * The AI engine screen: a segmented On device / OpenAI switch; On device shows the active model
 * first, then the other models with plain names, inline downloads and storage. Onboarding picks
 * and downloads a model by itself (ModelSetupManager); this is where the user changes it.
 *
 * @param onStorageClick opens the storage location picker
 * @param onOpenThread opens a thread after "Try on example" started its summary; without it a
 *   snackbar says a notification will follow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onBackClick: () -> Unit,
    viewModel: ModelDownloadViewModel = hiltViewModel(),
    onStorageClick: (() -> Unit)? = null,
    onOpenThread: ((threadId: String) -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Provider, OpenAI key and model live in the Settings view model
    val engineViewModel: SettingsViewModel = hiltViewModel()
    val tryStartedMessage = stringResource(R.string.engine_try_started)
    val resources = LocalResources.current

    LifecycleResumeEffect(viewModel) {
        // The storage location may have changed on the picker screen
        viewModel.refreshStorage()
        onPauseOrDispose { }
    }

    val actions = ModelActions(
        onTryExample = {
            viewModel.tryOnExample()?.let { threadId ->
                if (onOpenThread != null) {
                    onOpenThread(threadId)
                } else {
                    scope.launch { snackbarHostState.showSnackbar(tryStartedMessage) }
                }
            }
        },
        onUse = { model ->
            // Picking a model switches the engine to on-device: show it (e.g. "Try on example")
            viewModel.preloadModel(model) { engineViewModel.onEvent(SettingsEvent.Refresh) }
            val name = ModelProfiles.technicalName(model)
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.engine_now_using, name)) }
        },
        onStorageClick = onStorageClick
    )

    val pageColor = settingsPageColor()
    Scaffold(
        containerColor = pageColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.engine_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        EngineHub(
            viewModel = viewModel,
            engineViewModel = engineViewModel,
            actions = actions,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
        )
    }
}

/** What the model rows and the hero can do; navigation and feedback come from the screen. */
private class ModelActions(
    val onTryExample: () -> Unit,
    val onUse: (AIModel) -> Unit,
    val onStorageClick: (() -> Unit)?
)

@Composable
private fun EngineHub(
    viewModel: ModelDownloadViewModel,
    engineViewModel: SettingsViewModel,
    actions: ModelActions,
    modifier: Modifier = Modifier
) {
    val engineState by engineViewModel.uiState.collectAsStateWithLifecycle()
    val success = engineState as? SettingsUiState.Success
    val provider = success?.aiProvider ?: AIProvider.LOCAL
    // The tab the user is looking at; follows the active provider until they pick one
    var tab by rememberSaveable { mutableStateOf<AIProvider?>(null) }
    val shownTab = tab ?: provider
    val activeLocalModelId by viewModel.activeModelId.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = shownTab == AIProvider.LOCAL,
                onClick = {
                    tab = AIProvider.LOCAL
                    // Only switch when a local model is ready; otherwise the user is just looking,
                    // and the engine switches once they pick a model
                    if (success != null && provider != AIProvider.LOCAL && activeLocalModelId != null) {
                        engineViewModel.onEvent(SettingsEvent.SelectProvider(AIProvider.LOCAL))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text(stringResource(R.string.engine_tab_on_device)) }
            )
            SegmentedButton(
                selected = shownTab == AIProvider.OPENAI,
                onClick = {
                    tab = AIProvider.OPENAI
                    // Without a key OpenAI cannot run; it is switched on once a key is saved
                    if (success?.hasApiKey == true && provider != AIProvider.OPENAI) {
                        engineViewModel.onEvent(SettingsEvent.SelectProvider(AIProvider.OPENAI))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text(stringResource(R.string.engine_tab_openai)) }
            )
        }

        when (shownTab) {
            AIProvider.LOCAL -> OnDeviceContent(
                viewModel = viewModel,
                localEngineActive = provider == AIProvider.LOCAL,
                actions = actions
            )
            AIProvider.OPENAI -> when (val state = engineState) {
                is SettingsUiState.Success -> OpenAIEngineTab(state = state, onEvent = engineViewModel::onEvent)
                is SettingsUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(state.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = engineViewModel::dismissError) { Text(stringResource(R.string.retry)) }
                }
                SettingsUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun OnDeviceContent(
    viewModel: ModelDownloadViewModel,
    localEngineActive: Boolean,
    actions: ModelActions
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val isWiFiOnly by viewModel.isWiFiOnly.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeModelId.collectAsStateWithLifecycle()
    val sampleThreadId by viewModel.sampleThreadId.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val deviceRamGb = rememberDeviceRamGb()

    val sorted = remember(models) { ModelProfiles.sorted(models) }
    val active = sorted.firstOrNull { it.id == activeModelId && it.isDownloaded }
    val others = sorted.filter { it.id != active?.id }

    if (active != null) {
        ActiveModelHero(
            model = active,
            canTryExample = localEngineActive && sampleThreadId != null,
            onTryExample = actions.onTryExample,
            onDelete = if (active.sizeInMB > 0) {
                { viewModel.deleteModel(active.id) }
            } else {
                null
            }
        )
    } else {
        NoModelHero()
    }

    if (others.isNotEmpty()) {
        SettingsGroup(
            label = stringResource(if (active != null) R.string.engine_other_models else R.string.engine_models)
        ) {
            others.forEach { model ->
                ModelRow(
                    model = model,
                    downloadState = downloadStates[model.id] ?: ModelDownloadState(model.id),
                    deviceRamGb = deviceRamGb,
                    onDownload = { viewModel.downloadModel(model) },
                    onPause = { viewModel.pauseDownload(model.id) },
                    onResume = { viewModel.resumeDownload(model.id) },
                    onCancel = { viewModel.cancelDownload(model.id) },
                    onUse = { actions.onUse(model) },
                    onDelete = { viewModel.deleteModel(model.id) }
                )
            }
        }
    }

    storage?.let { usage ->
        StorageSection(
            storage = usage,
            isWiFiOnly = isWiFiOnly,
            onToggleWiFiOnly = viewModel::toggleWiFiOnly,
            onStorageClick = actions.onStorageClick
        )
    }
}

/** Total device RAM in whole GB, see [DeviceMemory.totalRamGb]. */
@Composable
private fun rememberDeviceRamGb(): Int {
    val context = LocalContext.current
    return remember(context) { DeviceMemory.totalRamGb(context) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveModelHero(
    model: AIModel,
    canTryExample: Boolean,
    onTryExample: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val profile = ModelProfiles.profileOf(model)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.engine_active_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                SpecChip(icon = Icons.Default.Check, text = stringResource(R.string.engine_ready))
                if (onDelete != null) {
                    DeleteMenu(onDelete = onDelete)
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Text(
                text = ModelProfiles.technicalName(model),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 8.dp)
            )
            heroSubtitle(profile)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(end = 8.dp, top = 2.dp)
                        .alpha(0.85f)
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 12.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profile.typicalSeconds?.let { seconds ->
                    SpecChip(
                        icon = Icons.Outlined.Timer,
                        text = stringResource(R.string.engine_time_per_summary, typicalTimeLabel(seconds))
                    )
                }
                when (profile.backend) {
                    ModelBackend.GPU -> SpecChip(icon = Icons.Default.Bolt, text = stringResource(R.string.engine_backend_gpu))
                    ModelBackend.CPU -> SpecChip(icon = Icons.Default.Memory, text = stringResource(R.string.engine_backend_cpu))
                    ModelBackend.DEMO -> Unit
                }
                SpecChip(icon = Icons.Default.SdStorage, text = sizeLabel(model))
            }
            if (canTryExample) {
                Button(onClick = onTryExample, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.engine_try_example))
                }
            }
        }
    }
}

@Composable
private fun heroSubtitle(profile: ModelProfile): String? {
    val plain = profile.plainName?.let { stringResource(it) }
    val language = profile.languageNote?.let { stringResource(it) }
    return when {
        plain != null && language != null -> stringResource(R.string.engine_hero_subtitle, plain, language)
        else -> plain ?: language
    }
}

@Composable
private fun NoModelHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = stringResource(R.string.engine_none_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.engine_none_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A small label on the hero card ("GPU", "~40 s per summary"). */
@Composable
private fun SpecChip(icon: ImageVector?, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DeleteMenu(onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.engine_delete)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: AIModel,
    downloadState: ModelDownloadState,
    deviceRamGb: Int,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    val profile = ModelProfiles.profileOf(model)
    val needsMoreRam = ModelProfiles.needsMoreRam(model, deviceRamGb)
    val downloaded = model.isDownloaded || downloadState.status == DownloadStatus.COMPLETED
    val inProgress = !downloaded &&
        (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.PAUSED)
    val title = profile.plainName?.let { stringResource(it) } ?: ModelProfiles.technicalName(model)

    val animatedProgress by animateFloatAsState(
        targetValue = downloadState.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "downloadProgress"
    )

    Column(
        modifier = Modifier
            .settingsGroupItem()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (needsMoreRam) 0.5f else 1f)
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = specLine(model, profile, includeTechnicalName = profile.plainName != null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (needsMoreRam) {
                    Text(
                        text = stringResource(R.string.engine_needs_ram, model.minimumRAM, deviceRamGb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    downloaded -> {
                        FilledTonalButton(onClick = onUse) { Text(stringResource(R.string.engine_use)) }
                        if (model.sizeInMB > 0) DeleteMenu(onDelete = onDelete) else Spacer(Modifier.width(8.dp))
                    }
                    downloadState.status == DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.models_pause))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.engine_cancel_cd))
                        }
                    }
                    downloadState.status == DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.engine_resume_cd))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.engine_cancel_cd))
                        }
                    }
                    needsMoreRam -> Unit
                    downloadState.status == DownloadStatus.FAILED -> OutlinedIconButton(onClick = onDownload) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.engine_retry_cd))
                    }
                    else -> OutlinedIconButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.engine_download_cd, title)
                        )
                    }
                }
            }
        }

        if (inProgress) {
            val percent = (downloadState.progress * 100).toInt()
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 8.dp)
            )
            Text(
                text = if (downloadState.status == DownloadStatus.DOWNLOADING) {
                    stringResource(
                        R.string.engine_downloading,
                        StorageHelper.formatStorageSize(downloadState.downloadedBytes / (1024 * 1024)),
                        StorageHelper.formatStorageSize(downloadState.totalBytes / (1024 * 1024)),
                        percent
                    )
                } else {
                    stringResource(R.string.models_paused, percent)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (!downloaded && downloadState.status == DownloadStatus.FAILED) {
            Text(
                text = stringResource(R.string.models_download_failed, downloadState.error.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
        }
    }
}

/** "~40 s · Gemma 4 E2B · GPU · 2.0 GB": the facts that tell models apart. */
@Composable
private fun specLine(model: AIModel, profile: ModelProfile, includeTechnicalName: Boolean): String {
    val parts = buildList {
        profile.typicalSeconds?.let { add(typicalTimeLabel(it)) }
        if (includeTechnicalName) add(ModelProfiles.technicalName(model))
        when (profile.backend) {
            ModelBackend.GPU -> add(stringResource(R.string.engine_backend_gpu))
            ModelBackend.CPU -> add(stringResource(R.string.engine_backend_cpu))
            ModelBackend.DEMO -> Unit
        }
        add(sizeLabel(model))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun typicalTimeLabel(seconds: Int): String = when (val time = TypicalTime.of(seconds)) {
    is TypicalTime.Seconds -> stringResource(R.string.engine_time_seconds, time.value)
    is TypicalTime.Minutes -> stringResource(R.string.engine_time_minutes, time.value)
}

@Composable
private fun sizeLabel(model: AIModel): String =
    if (model.sizeInMB > 0) formatModelSize(model.sizeInMB) else stringResource(R.string.engine_no_download)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageSection(
    storage: StorageUsage,
    isWiFiOnly: Boolean,
    onToggleWiFiOnly: () -> Unit,
    onStorageClick: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val (modelsShare, otherShare, freeShare) = storage.fractions()
    val locationName = stringResource(
        if (storage.location == StorageHelper.StorageLocation.EXTERNAL) {
            R.string.engine_storage_external
        } else {
            R.string.engine_storage_internal
        }
    )

    SettingsGroup(label = stringResource(R.string.engine_storage_title)) {
        Column(
            modifier = Modifier
                .settingsGroupItem()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(modelsShare to colors.primary, otherShare to colors.outlineVariant, freeShare to colors.surfaceContainerHighest)
                    .filter { (share, _) -> share > 0f }
                    .forEach { (share, color) ->
                        Box(
                            modifier = Modifier
                                .weight(share)
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
            }
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(colors.primary, stringResource(R.string.engine_storage_models, StorageHelper.formatStorageSize(storage.modelsMB)))
                LegendItem(colors.outlineVariant, stringResource(R.string.engine_storage_other))
                LegendItem(colors.surfaceContainerHighest, stringResource(R.string.engine_storage_free, StorageHelper.formatStorageSize(storage.freeMB)))
            }
        }
        SettingsRow(
            title = stringResource(R.string.engine_storage_location),
            icon = if (storage.location == StorageHelper.StorageLocation.EXTERNAL) Icons.Default.SdCard else Icons.Default.PhoneAndroid,
            value = stringResource(R.string.engine_storage_location_value, locationName),
            // Only worth opening when there is an SD card to choose
            onClick = onStorageClick?.takeIf { storage.externalAvailable }
        )
        SettingsRow(
            title = stringResource(R.string.engine_wifi_only),
            icon = Icons.Default.Wifi,
            value = stringResource(if (isWiFiOnly) R.string.engine_wifi_only_on else R.string.engine_wifi_only_off),
            trailing = SettingsRowTrailing.Switch(checked = isWiFiOnly, onCheckedChange = { onToggleWiFiOnly() })
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
