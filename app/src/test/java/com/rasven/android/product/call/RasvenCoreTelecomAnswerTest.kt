package com.rasven.android.product.call

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenCoreTelecomAnswerTest {
    @Test fun platformOriginatedAnswerOnlyAcceptsTheProductRequestOnce() = runBlocking {
        val events = mutableListOf<String>()

        val answered = coordinateIncomingCallAnswer(
            answerInRasven = { events += "rasven"; true },
            disconnectOnFailure = { events += "disconnect"; true },
        )

        assertTrue(answered)
        assertEquals(listOf("rasven"), events)
    }

    @Test fun telecomAnswerCallbackDoesNotWaitForQueuedMediaWork() = runBlocking {
        val events = mutableListOf<String>()
        var mediaFinished = false

        val answered = coordinateIncomingCallAnswer(
            answerInRasven = {
                events += "media-queued"
                launch {
                    delay(100)
                    mediaFinished = true
                }
                true
            },
            disconnectOnFailure = { events += "disconnect"; true },
        )

        assertTrue(answered)
        assertFalse(mediaFinished)
        assertEquals(listOf("media-queued"), events)
        delay(125)
        assertTrue(mediaFinished)
    }

    @Test fun failedProductAnswerCleansUpTheSignalingLeg() = runBlocking {
        val events = mutableListOf<String>()

        val answered = coordinateIncomingCallAnswer(
            answerInRasven = { events += "rasven"; false },
            disconnectOnFailure = { events += "disconnect"; true },
        )

        assertFalse(answered)
        assertEquals(listOf("rasven", "disconnect"), events)
    }

    @Test fun cleanupFailureDoesNotHideTheRejectedAnswer() = runBlocking {
        val events = mutableListOf<String>()

        val answered = coordinateIncomingCallAnswer(
            answerInRasven = { events += "rasven"; false },
            disconnectOnFailure = { events += "disconnect"; error("cleanup failed") },
        )

        assertFalse(answered)
        assertEquals(listOf("rasven", "disconnect"), events)
    }

    @Test fun thrownProductAnswerStillCleansUpIncomingSignaling() = runBlocking {
        val events = mutableListOf<String>()

        val answered = coordinateIncomingCallAnswer(
            answerInRasven = { events += "rasven"; error("product answer failed") },
            disconnectOnFailure = { events += "disconnect"; true },
        )

        assertFalse(answered)
        assertEquals(listOf("rasven", "disconnect"), events)
    }
}
