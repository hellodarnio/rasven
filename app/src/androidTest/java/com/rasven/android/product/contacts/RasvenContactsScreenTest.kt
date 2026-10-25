package com.rasven.android.product.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RasvenContactsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val fixtures = listOf(
        RasvenContact("علی رضایی", "09121234567"),
        RasvenContact("پشتیبانی فروش", "+982188880000"),
        RasvenContact("مریم احمدی", "09351234567"),
    )

    private fun changedPixelXs(first: ImageBitmap, second: ImageBitmap): List<Int> {
        val before = first.toPixelMap()
        val after = second.toPixelMap()
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        return buildList {
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    val a = before[x, y]
                    val b = after[x, y]
                    val delta = abs(a.red - b.red) + abs(a.green - b.green) +
                        abs(a.blue - b.blue) + abs(a.alpha - b.alpha)
                    if (delta > 0.08f) add(x)
                }
            }
        }
    }

    private fun render(
        granted: Boolean,
        contacts: List<RasvenContact> = fixtures,
        query: String = "",
        prefix: String = "9",
        onPermission: () -> Unit = {},
        onCall: (String) -> Unit = {},
        onNavigation: (RasvenNavDestination) -> Unit = {},
    ) {
        composeRule.setContent {
            RasvenTheme {
                RasvenContactsScreen(
                    permissionGranted = granted,
                    contacts = contacts,
                    query = query,
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onQueryChange = {},
                    onRequestPermission = onPermission,
                    contactPrefix = prefix,
                    onStartOutgoingCall = onCall,
                    onNavigationDestination = onNavigation,
                )
            }
        }
    }

    @Test fun permissionRequiredShowsApprovedCopyCtaAndContactsCurrentWithoutRows() {
        var requests = 0
        render(granted = false, onPermission = { requests += 1 })
        composeRule.onNodeWithTag("contacts_heading").assertIsDisplayed()
        composeRule.onNodeWithText("دسترسی مخاطبین داده نشده").assertIsDisplayed()
        composeRule.onNodeWithText("برای نمایش و شماره‌گیری مخاطبین، دسترسی مخاطبین را فعال کنید.").assertIsDisplayed()
        composeRule.onNodeWithTag("contacts_permission_cta").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.onNodeWithTag("contacts_search_field").assertDoesNotExist()
        composeRule.onNodeWithTag("contacts_list").assertDoesNotExist()
        composeRule.onNodeWithTag("contacts_bottom_navigation").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun permissionGrantTransitionShowsContactsWithoutAppRestart() {
        composeRule.setContent {
            RasvenTheme {
                var granted by remember { mutableStateOf(false) }
                RasvenContactsScreen(
                    permissionGranted = granted,
                    contacts = if (granted) fixtures else emptyList(),
                    query = "",
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onQueryChange = {},
                    onRequestPermission = { granted = true },
                    contactPrefix = "9",
                    onStartOutgoingCall = {},
                    onNavigationDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag("contacts_permission_cta").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("contacts_permission_card").assertDoesNotExist()
        composeRule.onNodeWithText("دفترچه تلفن گوشی").assertIsDisplayed()
        composeRule.onNodeWithText("علی رضایی").assertIsDisplayed()
    }

    @Test fun grantedScreenUsesApprovedSearchGuidanceFixturesAndBottomNav() {
        render(granted = true)
        composeRule.onNodeWithTag("contacts_heading").assertIsDisplayed()
        composeRule.onNodeWithText("دفترچه تلفن گوشی").assertIsDisplayed()
        composeRule.onNodeWithText("جستجو").assertIsDisplayed()
        composeRule.onNodeWithText("نام یا شماره").assertIsDisplayed()
        composeRule.onNodeWithText("پیش‌شماره خروجی فقط هنگام تماس از مخاطبین گوشی اعمال می‌شود؛ داخلی‌ها بدون تغییر می‌مانند.").assertIsDisplayed()
        fixtures.forEach { composeRule.onNodeWithText(it.displayName).assertIsDisplayed() }
        composeRule.onNodeWithTag("contacts_search_field").assertIsDisplayed()
        composeRule.onNodeWithTag("contacts_bottom_navigation").assertIsDisplayed()
    }

    @Test fun persianSearchPlaceholderGlyphsStartOnTheRightSideOfTheInput() {
        val queryState = mutableStateOf("")
        composeRule.setContent {
            RasvenTheme {
                RasvenContactsScreen(
                    permissionGranted = true,
                    contacts = fixtures,
                    query = queryState.value,
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onQueryChange = { queryState.value = it },
                    onRequestPermission = {},
                    contactPrefix = "9",
                    onStartOutgoingCall = {},
                    onNavigationDestination = {},
                )
            }
        }
        val withPlaceholder = composeRule.onNodeWithTag("contacts_search_field").captureToImage()
        composeRule.runOnIdle { queryState.value = " " }
        composeRule.waitForIdle()
        val withoutPlaceholder = composeRule.onNodeWithTag("contacts_search_field").captureToImage()
        val placeholderGlyphX = changedPixelXs(withPlaceholder, withoutPlaceholder)

        assertTrue("placeholder glyph difference should render", placeholderGlyphX.isNotEmpty())
        assertTrue(
            "Persian placeholder glyphs should stay in the right half of the input",
            placeholderGlyphX.minOrNull()!! > withPlaceholder.width / 2,
        )
    }

    @Test fun typedPersianSearchGlyphsStayRightAligned() {
        val queryState = mutableStateOf(" ")
        composeRule.setContent {
            RasvenTheme {
                RasvenContactsScreen(
                    permissionGranted = true,
                    contacts = fixtures,
                    query = queryState.value,
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onQueryChange = { queryState.value = it },
                    onRequestPermission = {},
                    contactPrefix = "9",
                    onStartOutgoingCall = {},
                    onNavigationDestination = {},
                )
            }
        }
        val blankFilled = composeRule.onNodeWithTag("contacts_search_field").captureToImage()
        composeRule.runOnIdle { queryState.value = "مریم" }
        composeRule.waitForIdle()
        val withPersianValue = composeRule.onNodeWithTag("contacts_search_field").captureToImage()
        val valueGlyphX = changedPixelXs(withPersianValue, blankFilled)

        assertTrue("typed Persian glyph difference should render", valueGlyphX.isNotEmpty())
        assertTrue(
            "typed Persian glyphs should stay in the right half of the input",
            valueGlyphX.minOrNull()!! > withPersianValue.width / 2,
        )
    }

    @Test fun searchTypingFiltersVisibleRowsStatefully() {
        composeRule.setContent {
            RasvenTheme {
                var query by remember { mutableStateOf("") }
                RasvenContactsScreen(
                    permissionGranted = true,
                    contacts = filterContacts(fixtures, query),
                    query = query,
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onQueryChange = { query = it },
                    onRequestPermission = {},
                    contactPrefix = "9",
                    onStartOutgoingCall = {},
                    onNavigationDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag("contacts_search_input").performClick().performTextInput("مریم")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("مریم احمدی").assertIsDisplayed()
        composeRule.onNodeWithText("علی رضایی").assertDoesNotExist()
        composeRule.onNodeWithText("پشتیبانی فروش").assertDoesNotExist()
    }

    @Test fun contactTapCallsOutgoingBoundaryExactlyOnceWithNormalizedPrefixedDestination() {
        var calls = 0
        var destination = ""
        render(granted = true, onCall = { calls += 1; destination = it })
        composeRule.onNodeWithTag("contact_row_+982188880000").performClick()
        composeRule.runOnIdle {
            assertEquals(1, calls)
            assertEquals("902188880000", destination)
        }
    }

    @Test fun contactsNavigationCanReachHomeAndSettings() {
        var selected: RasvenNavDestination? = null
        render(granted = true, onNavigation = { selected = it })
        composeRule.onNodeWithText("خانه").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Home, selected) }
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Settings, selected) }
    }

    @Test fun contactsHistoryNavigationCallbackDoesNotTouchPrefixPipeline() {
        var selected: RasvenNavDestination? = null
        render(granted = true, prefix = "9", onNavigation = { selected = it })
        composeRule.onNodeWithText("سوابق").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.History, selected) }
        // Existing Contacts-only behavior remains explicit and unchanged.
        assertEquals("305", prepareContactDialDestination("305", "9"))
        assertEquals("909121234567", prepareContactDialDestination("09121234567", "9"))
    }

    @Test fun emptyGrantedProviderResultIsSafeAndKeepsApprovedShell() {
        render(granted = true, contacts = emptyList())
        composeRule.onNodeWithText("دفترچه تلفن گوشی").assertIsDisplayed()
        composeRule.onNodeWithTag("contacts_list").assertIsDisplayed()
        composeRule.onNodeWithTag("contacts_bottom_navigation").assertIsDisplayed()
    }
}
