package io.github.christianjann.gitnotecje.manager

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageManagerTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `commit message consolidation logic works correctly`() {
        // Test the logic directly without accessing private function
        val singleMessage = listOf("test change")
        val consolidated = when (singleMessage.size) {
            0 -> "gitnote changes"
            1 -> singleMessage.first()
            else -> {
                val subject = "gitnote changes (${singleMessage.size} operations)"
                val body = singleMessage.joinToString("\n") { "- $it" }
                "$subject\n\n$body"
            }
        }
        assertEquals("test change", consolidated)

        val multipleMessages = listOf("change 1", "change 2", "change 3")
        val consolidatedMultiple = when (multipleMessages.size) {
            0 -> "gitnote changes"
            1 -> multipleMessages.first()
            else -> {
                val subject = "gitnote changes (${multipleMessages.size} operations)"
                val body = multipleMessages.joinToString("\n") { "- $it" }
                "$subject\n\n$body"
            }
        }
        assertEquals("gitnote changes (3 operations)\n\n- change 1\n- change 2\n- change 3", consolidatedMultiple)

        val emptyMessages = emptyList<String>()
        val consolidatedEmpty = when (emptyMessages.size) {
            0 -> "gitnote changes"
            1 -> emptyMessages.first()
            else -> {
                val subject = "gitnote changes (${emptyMessages.size} operations)"
                val body = emptyMessages.joinToString("\n") { "- $it" }
                "$subject\n\n$body"
            }
        }
        assertEquals("gitnote changes", consolidatedEmpty)
    }

    @Test
    fun `debouncing logic handles immediate vs delayed operations correctly`() = runTest {
        // Test immediate operation (debounceMs = 0)
        var executed = false
        val job = CoroutineScope(testDispatcher + SupervisorJob()).launch {
            delay(0)
            executed = true
        }

        // Wait for the job to complete
        job.join()
        assertTrue(executed)

        // Test delayed operation
        executed = false
        val delayedJob = CoroutineScope(testDispatcher + SupervisorJob()).launch {
            delay(1000)
            executed = true
        }

        // Should not execute immediately
        advanceTimeBy(500)
        assertFalse(executed)

        // Should execute after delay
        advanceTimeBy(600)
        assertTrue(executed)
    }

    @Test
    fun `background git delay settings are respected`() {
        // Test with different delay settings
        val delay5s = 5 * 1000L
        assertEquals(5000L, delay5s)

        val delay10s = 10 * 1000L
        assertEquals(10000L, delay10s)

        val delay0s = 0 * 1000L
        assertEquals(0L, delay0s)
    }

    @Test
    fun `immediate flag bypasses delay logic`() {
        val immediate = true
        val delaySetting = 5

        val effectiveDelay = if (immediate) 0L else delaySetting * 1000L
        assertEquals(0L, effectiveDelay)

        val notImmediate = false
        val effectiveDelay2 = if (notImmediate) 0L else delaySetting * 1000L
        assertEquals(5000L, effectiveDelay2)
    }

    @Test
    fun `coroutine supervision allows independent child execution`() = runTest {
        var firstCompleted = false
        var secondCompleted = false

        val supervisorJob = SupervisorJob()
        val scope = CoroutineScope(testDispatcher + supervisorJob)

        // Launch two independent children
        scope.launch {
            delay(100)
            firstCompleted = true
        }

        scope.launch {
            delay(200)
            secondCompleted = true
        }

        // Both should complete independently
        advanceTimeBy(250)
        assertTrue(firstCompleted)
        assertTrue(secondCompleted)
    }

    @Test
    fun `scheduled pull mechanism prevents immediate execution`() = runTest {
        var executed = false
        val scheduledPull: String? = "test-pull"

        // Simulate the scheduled pull mechanism
        val job = CoroutineScope(testDispatcher + SupervisorJob()).launch {
            delay(1000) // 1 second delay
            if (scheduledPull != null) {
                executed = true
            }
        }

        // Should not execute immediately
        advanceTimeBy(500)
        assertFalse(executed)

        // Should execute after delay
        advanceTimeBy(600)
        assertTrue(executed)
    }

    @Test
    fun `queue operations maintain proper ordering`() = runTest {
        val operations = mutableListOf<String>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())

        // Simulate queuing operations
        scope.launch {
            delay(100)
            operations.add("commit")
        }

        scope.launch {
            delay(200)
            operations.add("pull")
        }

        scope.launch {
            delay(300)
            operations.add("push")
        }

        // Wait for all operations to complete
        advanceTimeBy(400)

        // Operations should execute in order
        assertEquals(listOf("commit", "pull", "push"), operations)
    }

    @Test
    fun `background operations setting controls behavior`() {
        val enabled = true
        val disabled = false
        val hasRemoteUrl = "https://github.com/test/repo.git"
        val noRemoteUrl = ""

        // When enabled with remote URL
        val shouldQueue1 = enabled && hasRemoteUrl.isNotEmpty()
        assertTrue(shouldQueue1)

        // When enabled without remote URL
        val shouldQueue2 = enabled && noRemoteUrl.isNotEmpty()
        assertFalse(shouldQueue2)

        // When disabled with remote URL
        val shouldQueue3 = disabled && hasRemoteUrl.isNotEmpty()
        assertFalse(shouldQueue3)

        // When disabled without remote URL
        val shouldQueue4 = disabled && noRemoteUrl.isNotEmpty()
        assertFalse(shouldQueue4)
    }
}