package com.summarizer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.summarizer.app.R
import com.summarizer.app.util.PermissionHelper

/** Asks for notification access, without which no messages are collected. */
@Composable
fun PermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Outlined.NotificationsOff, contentDescription = null)
                Text(
                    text = stringResource(R.string.permission_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                text = stringResource(R.string.permission_card_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { PermissionHelper.openNotificationListenerSettings(context) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.permission_card_open_settings))
            }
        }
    }
}
