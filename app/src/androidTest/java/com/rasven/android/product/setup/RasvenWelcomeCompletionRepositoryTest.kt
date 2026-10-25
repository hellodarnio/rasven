package com.rasven.android.product.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenWelcomeCompletionRepositoryTest {
    @Test fun completionSurvivesRepositoryRecreationAndFreshDataStartsUnseen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("rasven_welcome_v1", Context.MODE_PRIVATE)
        val previous = preferences.getBoolean("completed", false)
        try {
            preferences.edit().clear().commit()
            assertFalse(RasvenWelcomeCompletionRepository(context).isCompleted())
            assertTrue(RasvenWelcomeCompletionRepository(context).complete())
            assertTrue(RasvenWelcomeCompletionRepository(context).isCompleted())
        } finally {
            preferences.edit().putBoolean("completed", previous).commit()
        }
    }
}
