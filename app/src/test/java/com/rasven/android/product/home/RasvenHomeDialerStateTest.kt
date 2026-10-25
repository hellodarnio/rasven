package com.rasven.android.product.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.product.setup.RasvenRegistrationState

class RasvenHomeDialerStateTest {
    @Test fun destinationReducerAppendsExactDialCharactersWithoutPrefix() {
        var value = ""
        value = appendDialCharacter(value, "3")
        assertEquals("3", value)
        value = appendDialCharacter(value, "0")
        assertEquals("30", value)
        value = appendDialCharacter(value, "5")
        assertEquals("305", value)
        value = appendDialCharacter(value, "*")
        value = appendDialCharacter(value, "#")
        assertEquals("305*#", value)
    }

    @Test fun deleteRemovesOneCharacterAndIsSafeWhenEmpty() {
        var value = "305"
        value = deleteLastDialCharacter(value); assertEquals("30", value)
        value = deleteLastDialCharacter(value); assertEquals("3", value)
        value = deleteLastDialCharacter(value); assertEquals("", value)
        value = deleteLastDialCharacter(value); assertEquals("", value)
    }

    @Test fun callEnablementTracksOnlyDestinationEmptiness() {
        assertFalse(callEnabled(""))
        assertTrue(callEnabled("305"))
    }

    @Test fun dndTogglePreservesDestination() {
        val owner = RasvenHomeDialerViewModel()
        owner.enterHome("204")
        owner.setDestination("305")
        owner.setDoNotDisturb(true)
        assertTrue(owner.localState.doNotDisturb)
        assertEquals("305", owner.localState.destination)
        owner.setDoNotDisturb(false)
        assertFalse(owner.localState.doNotDisturb)
        assertEquals("305", owner.localState.destination)
    }

    @Test fun directInputFilterDoesNotApplyContactPrefixOrRewriteAllowedCharacters() {
        assertEquals("305*#+", sanitizeDirectDialInput("305*#+"))
        assertEquals("۱۲۳", sanitizeDirectDialInput("۱۲۳"))
    }

    @Test fun outgoingCallCallbackFiresExactlyOnceWithExactDirectDestination() {
        var calls = 0
        var received = ""
        assertTrue(invokeOutgoingCallIfEnabled("305") { calls += 1; received = it })
        assertEquals(1, calls)
        assertEquals("305", received)
        assertFalse(invokeOutgoingCallIfEnabled("") { calls += 1 })
        assertEquals(1, calls)
    }

    @Test fun registrationTruthMapsToOneHomePresentationFamily() {
        assertEquals(RasvenHomeConnectionPresentation.Connected, homeConnectionPresentation(RasvenRegistrationState.Registered))
        assertEquals(RasvenHomeConnectionPresentation.Disconnected, homeConnectionPresentation(RasvenRegistrationState.Unregistered))
        assertEquals(RasvenHomeConnectionPresentation.Reconnecting, homeConnectionPresentation(RasvenRegistrationState.Registering))
    }

    @Test fun filledConnectedDestinationUsesHomeOnlyTertiaryColorContract() {
        assertEquals(RasvenSemanticColors.textTertiary, homeDestinationValueColorToken(true, "305"))
        assertEquals(null, homeDestinationValueColorToken(true, ""))
        assertEquals(null, homeDestinationValueColorToken(false, "305"))
    }

    @Test fun connectionPresentationDoesNotMutateRetainedDestinationOrDndState() {
        val owner = RasvenHomeDialerViewModel()
        owner.enterHome("204")
        owner.setDestination("305")
        owner.setDoNotDisturb(true)
        homeConnectionPresentation(RasvenRegistrationState.Unregistered)
        homeConnectionPresentation(RasvenRegistrationState.Registering)
        homeConnectionPresentation(RasvenRegistrationState.Registered)
        assertEquals("305", owner.localState.destination)
        assertTrue(owner.localState.doNotDisturb)
    }

    @Test fun realNavigationOwnerMovesHomeToContactsAndBackWithoutChangingDialState() {
        val owner = RasvenHomeDialerViewModel()
        owner.enterHome("204")
        owner.setDestination("305")
        owner.setDoNotDisturb(true)
        owner.navigateTo(RasvenNavDestination.Contacts)
        assertEquals(RasvenNavDestination.Contacts, owner.currentDestination)
        owner.navigateTo(RasvenNavDestination.Home)
        assertEquals(RasvenNavDestination.Home, owner.currentDestination)
        assertEquals("305", owner.localState.destination)
        assertTrue(owner.localState.doNotDisturb)
    }

    @Test fun persianDisplayDigitsAffectDisplayOnly() {
        assertEquals("۲۰۴", toPersianDisplayDigits("204"))
        assertEquals("ext-۲۰۴", toPersianDisplayDigits("ext-204"))
    }

    @Test fun navigationOwnerSupportsHistoryWithoutChangingDirectDialState() {
        val vm = RasvenHomeDialerViewModel()
        vm.enterHome("204")
        vm.setDestination("305")
        vm.navigateTo(com.rasven.android.designsystem.components.RasvenNavDestination.History)
        assertEquals(com.rasven.android.designsystem.components.RasvenNavDestination.History, vm.currentDestination)
        assertEquals("305", vm.localState.destination)
        vm.navigateTo(com.rasven.android.designsystem.components.RasvenNavDestination.Home)
        assertEquals("305", vm.localState.destination)
    }
}
