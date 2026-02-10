package com.summarizer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.summarizer.app.ui.navigation.NavGraph
import com.summarizer.app.ui.theme.SummarizerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_NAVIGATE_TO_THREADS = "extra_navigate_to_threads"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get navigation extras from intent
        val threadId = intent?.getStringExtra(EXTRA_THREAD_ID)
        val navigateToThreads = intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_THREADS, false) ?: false

        setContent {
            SummarizerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        initialThreadId = threadId,
                        navigateToThreadsOnStart = navigateToThreads
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle new intent by recreating activity
        recreate()
    }
}
