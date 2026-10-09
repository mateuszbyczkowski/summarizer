package com.summarizer.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.summarizer.app.R

/**
 * Text field for entering an OpenAI API key. The key is masked by default and the keyboard is
 * asked not to autocorrect or learn it.
 */
@Composable
fun ApiKeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.api_key_label)) },
        placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
        modifier = modifier,
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                    imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(
                        if (showApiKey) R.string.cd_hide_api_key else R.string.cd_show_api_key
                    )
                )
            }
        },
        visualTransformation = if (showApiKey) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        singleLine = true,
        isError = isError
    )
}

/** Step-by-step instructions for creating an OpenAI API key. */
@Composable
fun ApiKeyHowToCard(modifier: Modifier = Modifier) {
    InfoCard(
        title = stringResource(R.string.api_key_how_to_title),
        text = stringResource(R.string.api_key_how_to_steps),
        tone = InfoCardTone.Info,
        modifier = modifier
    )
}

/**
 * Warns that the OpenAI provider sends messages off the device. [suggestion] tells the user how
 * to keep everything private from the current screen.
 */
@Composable
fun OpenAIPrivacyNoticeCard(
    suggestion: String,
    modifier: Modifier = Modifier
) {
    InfoCard(
        title = stringResource(R.string.openai_privacy_title),
        text = stringResource(R.string.openai_privacy_body, suggestion),
        tone = InfoCardTone.Warning,
        modifier = modifier
    )
}
