package com.rasven.android.iax2

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.app.BootstrapActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device smoke only: no audio samples are persisted or asserted. */
@RunWith(AndroidJUnit4::class)
class RasvenAndroidAudioMediaSmokeTest {
    @Test
    fun realAudioObjectsInitializeStartStopAndRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantRecordAudio(instrumentation, context.packageName)
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
            val factory = RasvenAndroidAudioIoFactory(context)
            val environment = factory.createEnvironment()
            assertTrue(environment.activate { })
            val output = factory.createOutput(format)
            val input = factory.createInput(format)
            assertNotNull(output)
            assertNotNull(input)
            try {
                assertTrue(output!!.start())
                assertTrue(input!!.start())
            } finally {
                input?.close()
                output?.close()
                environment.close()
            }
        }
    }

    @Test
    fun userVisibleCallOwnershipStartsAndStopsMicrophoneForegroundService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantRecordAudio(instrumentation, context.packageName)
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(RasvenCallMediaForegroundService.start(context))
            assertTrue(waitUntil { RasvenCallMediaForegroundService.isActiveForDiagnostics() })
            RasvenCallMediaForegroundService.stop(context)
            assertTrue(waitUntil { !RasvenCallMediaForegroundService.isActiveForDiagnostics() })
        }
    }

    @Test
    fun immediateCancellationDoesNotLeaveAnUnpromotedForegroundStart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantRecordAudio(instrumentation, context.packageName)
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(RasvenCallMediaForegroundService.start(context))
            RasvenCallMediaForegroundService.stop(context)
            assertTrue(waitUntil { !RasvenCallMediaForegroundService.isActiveForDiagnostics() })
            // The old stopService race crashed the process only after Android's FGS deadline.
            Thread.sleep(12_000)
            instrumentation.waitForIdleSync()
            assertTrue(!RasvenCallMediaForegroundService.isActiveForDiagnostics())
        }
    }

    @Test
    fun openingHomeDoesNotStartMicrophoneForegroundService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        RasvenCallMediaForegroundService.stop(context)
        waitUntil { !RasvenCallMediaForegroundService.isActiveForDiagnostics() }
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            assertTrue(!RasvenCallMediaForegroundService.isActiveForDiagnostics())
        }
    }

    private fun grantRecordAudio(
        instrumentation: android.app.Instrumentation,
        packageName: String,
    ) {
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName ${Manifest.permission.RECORD_AUDIO}").close()
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(timeoutMs: Long = 3_000, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            Thread.sleep(25)
        }
        return predicate()
    }
}
