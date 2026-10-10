package com.summarizer.app.domain.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGateTest {

    @Test
    fun `jobs run one at a time, in the order they asked`() = runTest {
        val gate = ModelGate()
        val order = mutableListOf<String>()
        val firstRunning = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var secondWaited = false

        launch {
            gate.withModel {
                order += "first start"
                firstRunning.complete(Unit)
                releaseFirst.await()
                order += "first end"
            }
        }
        firstRunning.await()
        launch { gate.withModel(onWait = { secondWaited = true }) { order += "second" } }
        launch { gate.withModel { order += "third" } }
        runCurrent()

        assertTrue(secondWaited)
        assertEquals(listOf("first start"), order)
        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("first start", "first end", "second", "third"), order)
    }

    @Test
    fun `a free model runs at once without waiting`() = runTest {
        var waited = false
        val result = ModelGate().withModel(onWait = { waited = true }) { 42 }
        assertEquals(42, result)
        assertFalse(waited)
    }

    @Test
    fun `tryWithModel skips while the model is busy`() = runTest {
        val gate = ModelGate()
        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        launch {
            gate.withModel {
                running.complete(Unit)
                release.await()
            }
        }
        running.await()

        assertNull(gate.tryWithModel { "analysis" })
        release.complete(Unit)
        runCurrent()
        assertEquals("analysis", gate.tryWithModel { "analysis" })
    }

    @Test
    fun `a failing job frees the model`() = runTest {
        val gate = ModelGate()
        runCatching { gate.withModel { error("load failed") } }
        assertEquals("next", gate.tryWithModel { "next" })
    }
}
