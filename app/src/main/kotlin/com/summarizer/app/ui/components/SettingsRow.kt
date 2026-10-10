package com.summarizer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * Grouped settings list (Material 3 "list in a container" style): a primary-coloured group label
 * above rows that share one rounded container, separated by thin gaps. Rows show an icon in a
 * tonal circle, a title, the current value as the second line and a trailing switch, chevron or
 * status icon.
 */

private val GroupOuterCorner = 22.dp
private val GroupInnerCorner = 6.dp
private val GroupGap = 2.dp

/** Background of a screen that shows [SettingsGroup]s. */
@Composable
@ReadOnlyComposable
fun settingsPageColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.isDark()) colors.surface else colors.surfaceContainerLow
}

/** Background of one row (or card) inside a [SettingsGroup]. */
@Composable
@ReadOnlyComposable
fun settingsItemColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.isDark()) colors.surfaceContainerHigh else colors.surfaceContainerLowest
}

private fun androidx.compose.material3.ColorScheme.isDark(): Boolean = surface.luminance() < 0.5f

/** Gives any content the look of a row inside a [SettingsGroup]. */
@Composable
fun Modifier.settingsGroupItem(): Modifier =
    this
        .fillMaxWidth()
        .clip(RoundedCornerShape(GroupInnerCorner))
        .background(settingsItemColor())

/**
 * A titled group of rows. The rows (typically [SettingsRow]s, or content using
 * [settingsGroupItem]) are stacked with small gaps; the group rounds the outer corners.
 */
@Composable
fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupOuterCorner)),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
            content = content
        )
    }
}

/** What a [SettingsRow] shows at its end. */
sealed interface SettingsRowTrailing {
    data object None : SettingsRowTrailing

    /** Opens a sub-screen or dialog. */
    data object Chevron : SettingsRowTrailing

    /**
     * A switch. Without a row click the whole row toggles; with one, the row is split: the text
     * opens the row's detail (e.g. a time picker) and the switch toggles on its own.
     */
    data class Switch(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : SettingsRowTrailing

    /** A check (all good) or an error icon (needs attention). */
    data class Status(val ok: Boolean, val contentDescription: String) : SettingsRowTrailing
}

enum class SettingsRowTone { Default, Error }

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: SettingsRowTrailing = if (onClick != null) SettingsRowTrailing.Chevron else SettingsRowTrailing.None,
    tone: SettingsRowTone = SettingsRowTone.Default,
    enabled: Boolean = true
) {
    val switch = trailing as? SettingsRowTrailing.Switch
    val splitSwitch = switch != null && onClick != null

    val rowModifier = when {
        switch != null && !splitSwitch -> Modifier.toggleable(
            value = switch.checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = switch.onCheckedChange
        )
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }

    Row(
        modifier = modifier.settingsGroupItem(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(rowModifier)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = if (splitSwitch) 16.dp else 0.dp, top = 10.dp, bottom = 10.dp)
                .alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) SettingsRowIcon(icon, tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tone == SettingsRowTone.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!splitSwitch) {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    TrailingContent(trailing, enabled)
                }
            }
        }
        if (splitSwitch) {
            VerticalDivider(modifier = Modifier.height(32.dp))
            Switch(
                checked = switch.checked,
                onCheckedChange = switch.onCheckedChange,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun TrailingContent(trailing: SettingsRowTrailing, enabled: Boolean) {
    when (trailing) {
        SettingsRowTrailing.None -> Unit
        SettingsRowTrailing.Chevron -> Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SettingsRowTrailing.Switch -> Switch(
            checked = trailing.checked,
            // The whole row is the toggle target
            onCheckedChange = null,
            enabled = enabled
        )
        is SettingsRowTrailing.Status -> Icon(
            imageVector = if (trailing.ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = trailing.contentDescription,
            tint = if (trailing.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

/** An icon in a 40 dp tonal circle, as used at the start of settings rows. */
@Composable
fun SettingsRowIcon(icon: ImageVector, tone: SettingsRowTone = SettingsRowTone.Default) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        SettingsRowTone.Default -> colors.secondaryContainer to colors.onSecondaryContainer
        SettingsRowTone.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
    }
}
