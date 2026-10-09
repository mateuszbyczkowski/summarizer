package com.summarizer.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.summarizer.app.ui.lock.AppLockManager
import com.summarizer.app.ui.navigation.NavGraph
import com.summarizer.app.ui.navigation.PendingNavigation
import com.summarizer.app.ui.screens.auth.PinLockScreen
import com.summarizer.app.ui.theme.SummarizerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (rather than ComponentActivity) is required by BiometricPrompt
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_NAVIGATE_TO_THREADS = "extra_navigate_to_threads"
    }

    @Inject
    lateinit var appLockManager: AppLockManager

    private val pendingNavigation = mutableStateOf<PendingNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep message content out of screenshots, screen recordings and the recents thumbnail
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        if (savedInstanceState == null) {
            pendingNavigation.value = intent.toPendingNavigation()
        }

        setContent {
            SummarizerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()
                    // Keeps the navigation state while the lock screen replaces it
                    val stateHolder = rememberSaveableStateHolder()

                    when (isLocked) {
                        null -> Unit
                        true -> PinLockScreen(onUnlocked = appLockManager::unlock)
                        false -> stateHolder.SaveableStateProvider("app") {
                            NavGraph(
                                pendingNavigation = pendingNavigation.value,
                                onPendingNavigationHandled = { pendingNavigation.value = null }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toPendingNavigation()?.let { pendingNavigation.value = it }
    }

    private fun Intent.toPendingNavigation(): PendingNavigation? {
        val threadId = getStringExtra(EXTRA_THREAD_ID)
        return when {
            threadId != null -> PendingNavigation.Thread(threadId)
            getBooleanExtra(EXTRA_NAVIGATE_TO_THREADS, false) -> PendingNavigation.Threads
            else -> null
        }
    }
}
