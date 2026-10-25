package com.rasven.android.product.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenAdvancedSettingsValidationTest {
    @Test fun validServerPortUsernamePassWithoutPasswordReplacement() {
        assertTrue(RasvenAdvancedSettingsValidation.validate("pbx.example.net", "4569", "204").isValid)
    }

    @Test fun emptyServerIsRejected() {
        assertEquals(
            RasvenAccountFieldError.ServerRequired,
            RasvenAdvancedSettingsValidation.validate(" ", "4569", "204").server,
        )
    }

    @Test fun portOneIsAccepted() {
        assertNull(RasvenAdvancedSettingsValidation.validate("pbx", "1", "204").port)
    }

    @Test fun port65535IsAccepted() {
        assertNull(RasvenAdvancedSettingsValidation.validate("pbx", "65535", "204").port)
    }

    @Test fun portZeroIsRejected() {
        assertEquals(
            RasvenAccountFieldError.PortOutOfRange,
            RasvenAdvancedSettingsValidation.validate("pbx", "0", "204").port,
        )
    }

    @Test fun port65536IsRejected() {
        assertEquals(
            RasvenAccountFieldError.PortOutOfRange,
            RasvenAdvancedSettingsValidation.validate("pbx", "65536", "204").port,
        )
    }

    @Test fun nonNumericPortIsRejected() {
        assertEquals(
            RasvenAccountFieldError.PortOutOfRange,
            RasvenAdvancedSettingsValidation.validate("pbx", "abc", "204").port,
        )
    }

    @Test fun emptyUsernameIsRejected() {
        assertEquals(
            RasvenAccountFieldError.UsernameRequired,
            RasvenAdvancedSettingsValidation.validate("pbx", "4569", " ").username,
        )
    }

    @Test fun emptyReplacementMapsToNoPasswordChange() {
        val update = RasvenAdvancedSettingsValidation.validatedUpdate("pbx", "4569", "204", "")
        assertNull(update?.passwordReplacement)
    }

    @Test fun newReplacementIsForwardedExactlyWithoutTrim() {
        val replacement = " new secret "
        val update = RasvenAdvancedSettingsValidation.validatedUpdate("pbx", "4569", "204", replacement)
        assertEquals(replacement, update?.passwordReplacement)
    }

    @Test fun currentUiModelHasNoStoredPasswordRecoveryField() {
        val names = RasvenAdvancedSettingsCurrentAccount::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { it.contains("password") || it.contains("secret") })
    }

    @Test fun updatedValuesAreMappedAndNormalizedCorrectly() {
        val update = RasvenAdvancedSettingsValidation.validatedUpdate(" pbx.example.net ", "۴۵۶۹", " 204 ", "")
        requireNotNull(update)
        assertEquals("pbx.example.net", update.server)
        assertEquals(4569, update.port)
        assertEquals("204", update.username)
        assertNull(update.passwordReplacement)
    }

    @Test fun invalidFormDoesNotCreateUpdate() {
        assertNull(RasvenAdvancedSettingsValidation.validatedUpdate("", "70000", "", "new"))
    }

    @Test fun updateToStringNeverContainsReplacementSecret() {
        val secret = "never-print-this"
        val update = RasvenAdvancedSettingsValidation.validatedUpdate("pbx", "4569", "204", secret)
        requireNotNull(update)
        assertFalse(update.toString().contains(secret))
        assertTrue(update.toString().contains("passwordReplacement=[redacted]"))
    }
}
