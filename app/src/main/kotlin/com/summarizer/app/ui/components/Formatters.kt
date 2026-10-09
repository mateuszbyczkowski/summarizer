package com.summarizer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.summarizer.app.R

/** Formats an approximate model size, switching to GB with one decimal above 1000 MB. */
@Composable
fun formatModelSize(sizeInMB: Long): String =
    if (sizeInMB > 1000) {
        stringResource(R.string.size_gb, sizeInMB / 1000.0)
    } else {
        stringResource(R.string.size_mb, sizeInMB)
    }
