package com.rasven.android.product.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenPermissionsEducationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun content(
        requiredAccessReady: Boolean = false,
        onOpenSettings: () -> Unit = {},
        onRequestAccess: () -> Unit = {},
    ) {
        RasvenTheme {
            RasvenPermissionsEducationScreen(
                persianFontFamily = RasvenAndroidFontFamilies.Persian,
                requiredAccessReady = requiredAccessReady,
                onOpenRequiredAccessSettings = onOpenSettings,
                onRequestRequiredAccess = onRequestAccess,
            )
        }
    }

    @Test fun screenRendersCanonicalEducationContentAndOfficialActions() {
        composeRule.setContent { content() }
        composeRule.onNode(hasText("دسترسی‌های تماس") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("آماده‌سازی تماس‌های ورودی و خروجی").assertIsDisplayed()
        composeRule.onNodeWithText("برای اتصال خط و تماس دوطرفه، میکروفن و اعلان تماس باید فعال باشند. مخاطبین اختیاری است.").assertIsDisplayed()
        composeRule.onNodeWithText("ضروری: میکروفن برای مکالمهٔ دوطرفه").assertIsDisplayed()
        composeRule.onNodeWithText("ضروری: اعلان تماس و نمایش تماس ورودی روی صفحه").assertIsDisplayed()
        composeRule.onNodeWithText("اختیاری: مخاطبین فقط برای نمایش نام‌ها").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_open_required_settings").assertHasClickAction()
        composeRule.onNodeWithTag("permissions_continue_connect").assertHasClickAction()
        composeRule.onNodeWithTag("permissions_required_access_status").assertIsDisplayed()
    }

    @Test fun openingScreenInvokesNoContinuationCallback() {
        var openSettings = 0
        var connect = 0
        composeRule.setContent {
            content(
                onOpenSettings = { openSettings++ },
                onRequestAccess = { connect++ },
            )
        }
        composeRule.runOnIdle {
            assertEquals(0, openSettings)
            assertEquals(0, connect)
        }
    }

    @Test fun settingsActionOpensOnlyRequiredAccessSettings() {
        var openSettings = 0
        var connect = 0
        composeRule.setContent {
            content(
                onOpenSettings = { openSettings++ },
                onRequestAccess = { connect++ },
            )
        }
        composeRule.onNodeWithTag("permissions_open_required_settings").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openSettings)
            assertEquals(0, connect)
        }
    }

    @Test fun filledActionRequestsRequiredAccessAndConnectsExactlyOnce() {
        var openSettings = 0
        var connect = 0
        composeRule.setContent {
            content(
                onOpenSettings = { openSettings++ },
                onRequestAccess = { connect++ },
            )
        }
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.runOnIdle {
            assertEquals(0, openSettings)
            assertEquals(1, connect)
        }
    }

    @Test fun permissionRowsAreInformationalAndExposeNoClickAction() {
        composeRule.setContent { content() }
        for (tag in listOf(
            "permissions_microphone_row",
            "permissions_incoming_call_row",
            "permissions_contacts_row",
        )) {
            val config = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertFalse("$tag must remain educational, not interactive", config.contains(SemanticsActions.OnClick))
        }
    }

    @Test fun officialPermissionIconsRenderAt24DpInsideSharedGlassCard() {
        composeRule.setContent { content() }
        val bounds = listOf(
            "permissions_microphone_icon",
            "permissions_incoming_call_icon",
            "permissions_contacts_icon",
        ).map { tag -> composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.all { it.width > 0f && it.height > 0f })
        assertTrue(bounds.all { kotlin.math.abs(it.width - it.height) <= 1f })
        assertEquals(bounds[0].width, bounds[1].width, 1f)
        assertEquals(bounds[1].width, bounds[2].width, 1f)
    }

    @Test fun compact320DpViewportKeepsCardsAndActionsInsideBounds() {
        composeRule.setContent {
            Box(Modifier.width(320.dp).fillMaxHeight().testTag("permissions_320_viewport")) {
                content()
            }
        }
        val viewport = composeRule.onNodeWithTag("permissions_320_viewport").fetchSemanticsNode().boundsInRoot
        for (tag in listOf(
            "permissions_intro_card",
            "permissions_card",
            "permissions_open_required_settings",
            "permissions_continue_connect",
        )) {
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag starts inside compact viewport", bounds.left >= viewport.left - 1f)
            assertTrue("$tag ends inside compact viewport", bounds.right <= viewport.right + 1f)
        }
        composeRule.onNodeWithTag("permissions_open_required_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_continue_connect").assertIsDisplayed()
    }
}
