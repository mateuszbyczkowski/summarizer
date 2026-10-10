package com.summarizer.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user turned animations off ("Remove animations" in the accessibility settings,
 * which sets the animator duration scale to 0). Compose already finishes animations instantly
 * then; use this to skip effects such as staggered delays that would still hold content back.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
