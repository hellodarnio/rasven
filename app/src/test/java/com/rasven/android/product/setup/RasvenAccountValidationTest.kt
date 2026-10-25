package com.rasven.android.product.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenAccountValidationTest {
    @Test fun reloadedAccountWithIdenticalCredentialsKeepsTheSameRegistrationOwner() {
        val original = RasvenValidatedAccount("pbx.example.net", 4569, "fixture", "fixture secret ")
        val restored = RasvenValidatedAccount(original.server, original.port, original.username, original.password)
        assertFalse(original === restored)
        assertTrue(original.sameRegistrationAs(restored))
        assertTrue(restored.sameRegistrationAs(original))
        assertFalse(original.sameRegistrationAs(null))
    }

    @Test fun eachChangedCredentialRequiresADifferentRegistrationOwnerWithoutTrimmingPassword() {
        val original = RasvenValidatedAccount("pbx.example.net", 4569, "fixture", " fixture secret ")
        val changed = listOf(
            RasvenValidatedAccount("other.example.net", 4569, "fixture", " fixture secret "),
            RasvenValidatedAccount("pbx.example.net", 4570, "fixture", " fixture secret "),
            RasvenValidatedAccount("pbx.example.net", 4569, "different", " fixture secret "),
            RasvenValidatedAccount("pbx.example.net", 4569, "fixture", "fixture secret"),
        )
        changed.forEach { assertFalse(original.sameRegistrationAs(it)) }
    }

    @Test fun portLimits_areInclusive() {
        assertEquals(1, RasvenAccountValidation.parsePort("1"))
        assertEquals(65535, RasvenAccountValidation.parsePort("65535"))
    }

    @Test fun portOutsideLimits_isRejected() {
        assertNull(RasvenAccountValidation.parsePort("0"))
        assertNull(RasvenAccountValidation.parsePort("65536"))
        assertNull(RasvenAccountValidation.parsePort("-1"))
        assertNull(RasvenAccountValidation.parsePort("9999999999999999999"))
    }

    @Test fun nonNumericPort_isRejected() {
        for (candidate in listOf("", "a", "45x", "12.3", "+12")) {
            assertNull(RasvenAccountValidation.parsePort(candidate))
        }
    }

    @Test fun persianAndArabicIndicKeyboardDigits_parseWithoutLosingRangeCheck() {
        assertEquals(4569, RasvenAccountValidation.parsePort("۴۵۶۹"))
        assertEquals(4569, RasvenAccountValidation.parsePort("٤٥٦٩"))
        assertNull(RasvenAccountValidation.parsePort("۶۵۵۳۶"))
    }

    @Test fun allRequiredFields_haveDistinctErrors() {
        val errors = RasvenAccountValidation.validate(" ", "", "  ", "")
        assertFalse(errors.isValid)
        assertEquals(RasvenAccountFieldError.ServerRequired, errors.server)
        assertEquals(RasvenAccountFieldError.PortOutOfRange, errors.port)
        assertEquals(RasvenAccountFieldError.UsernameRequired, errors.username)
        assertEquals(RasvenAccountFieldError.PasswordRequired, errors.password)
    }

    @Test fun onlyIncorrectPort_isReportedWhenOtherFieldsAreValid() {
        val errors = RasvenAccountValidation.validate("pbx.example.net", "70000", "204", "p".repeat(8))
        assertNull(errors.server)
        assertEquals(RasvenAccountFieldError.PortOutOfRange, errors.port)
        assertNull(errors.username)
        assertNull(errors.password)
    }

    @Test fun validatedCallbackReceivesOnlyCompleteInputAndPasswordIsNotPrinted() {
        assertNull(RasvenAccountValidation.validatedInput("", "4569", "204", "p".repeat(8)))
        val validated = RasvenAccountValidation.validatedInput(" pbx.example.net ", "4569", " 204 ", "p".repeat(8))
        requireNotNull(validated)
        assertEquals("pbx.example.net", validated.server)
        assertEquals(4569, validated.port)
        assertEquals("204", validated.username)
        assertEquals("p".repeat(8), validated.password)
        assertFalse(validated.toString().contains("pbx.example.net"))
        assertFalse(validated.toString().contains("204"))
        assertFalse(validated.toString().contains("p".repeat(8)))
        assertTrue(validated.toString().contains("password=[redacted]"))
    }

    @Test fun nonemptyPasswordIsNotSilentlyTrimmed() {
        val validated = RasvenAccountValidation.validatedInput("pbx.example.net", "1", "204", " p ")
        assertEquals(" p ", validated?.password)
    }
}
