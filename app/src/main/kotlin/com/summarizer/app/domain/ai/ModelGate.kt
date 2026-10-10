package com.summarizer.app.domain.ai

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets one job at a time use the AI model, so the one loaded model is shared instead of two
 * jobs loading, swapping or running it at once (on-device generation also keeps the GPU busy).
 * Summaries queue up in the order they asked (the mutex is fair); loading or switching a model
 * waits for the running summary to finish.
 *
 * Not reentrant: code inside [withModel] calls the engine directly, never [withModel] again.
 */
@Singleton
class ModelGate @Inject constructor() {
    private val mutex = Mutex()

    /**
     * Runs [block] once no other job uses the model; [onWait] is called first when it has to
     * wait, so the caller can say it is queued.
     */
    suspend fun <T> withModel(onWait: () -> Unit = {}, block: suspend () -> T): T {
        if (!mutex.tryLock()) {
            onWait()
            mutex.lock()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    /** Runs [block] only if the model is free right now; null when another job uses it. */
    suspend fun <T> tryWithModel(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
