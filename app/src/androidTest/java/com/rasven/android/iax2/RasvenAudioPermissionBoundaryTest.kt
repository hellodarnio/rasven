package com.rasven.android.iax2

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenAudioPermissionBoundaryTest {
    @Test fun deniedMicrophoneReturnsBeforeAudioRecordConstruction() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        var checks = 0
        val denied = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun checkSelfPermission(permission: String): Int {
                assertEquals(Manifest.permission.RECORD_AUDIO, permission)
                checks += 1
                return PackageManager.PERMISSION_DENIED
            }
        }
        val input = RasvenAndroidAudioIoFactory(denied).createInput(RasvenAudioMediaFormat(RasvenAudioCodec.PCMU))
        assertNull(input)
        assertEquals(1, checks)
    }
}
