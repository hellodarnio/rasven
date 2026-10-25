package com.rasven.android.product.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBottomNavigation
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.components.RasvenTextField
import com.rasven.android.designsystem.components.RasvenTextFieldAppearance
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenProductLayout
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/**
 * One Contacts destination with permission-driven content.
 * Granted: 897:9393. Permission Required: 897:9512.
 */
@Composable
internal fun RasvenContactsScreen(
    permissionGranted: Boolean,
    contacts: List<RasvenContact>,
    query: String,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onQueryChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    contactPrefix: String,
    onStartOutgoingCall: (String) -> Unit,
    onNavigationDestination: (RasvenNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    onStartOutgoingContactCall: ((RasvenContact, String) -> Unit)? = null,
) {
    val pageFont = productPageFont(persianFontFamily)
    val dataFont = productDataFont(englishFontFamily)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .testTag("contacts_screen"),
    ) {
        val contentWidth = (maxWidth - 48.dp).coerceIn(
            RasvenProductLayout.contentMinWidth.dp,
            RasvenProductLayout.destinationInputMaxWidth.dp,
        )

        RasvenDailyAmbient()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            RasvenDailyBottomLayout(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                ContactsHeader(
                    permissionGranted = permissionGranted,
                    persianFontFamily = pageFont,
                )

                if (permissionGranted) {
                    GrantedContactsContent(
                        contacts = contacts,
                        query = query,
                        contentWidth = contentWidth,
                        persianFontFamily = pageFont,
                        englishFontFamily = dataFont,
                        onQueryChange = onQueryChange,
                        contactPrefix = contactPrefix,
                        onStartOutgoingCall = onStartOutgoingCall,
                        onStartOutgoingContactCall = onStartOutgoingContactCall,
                    )
                } else {
                    PermissionRequiredContent(
                        contentWidth = contentWidth,
                        persianFontFamily = pageFont,
                        onRequestPermission = onRequestPermission,
                    )
                }

                RasvenBottomNavigation(
                    language = productBadgeLanguage(),
                    selectedDestination = RasvenNavDestination.Contacts,
                    fontFamily = pageFont,
                    onDestinationClick = onNavigationDestination,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = dailyBottomLift())
                        .testTag("contacts_bottom_navigation"),
                )
            }
        }
    }
}

@Composable
private fun ContactsHeader(
    permissionGranted: Boolean,
    persianFontFamily: FontFamily,
) {
    val colors = RasvenDesignTheme.colors
    val heading = RasvenTypographyTokens.headingL
    val body = RasvenTypographyTokens.bodyS
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        BasicText(
            text = stringResource(R.string.contacts_heading),
            modifier = Modifier.absoluteOffset(x = 24.dp, y = 16.dp)
                .fillMaxWidth().padding(end = 48.dp)
                .semantics { heading() }
                .testTag("contacts_heading"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(heading.fontWeight),
                fontSize = heading.fontSizeSp.sp,
                lineHeight = heading.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
        )
        if (permissionGranted) {
            BasicText(
                text = stringResource(R.string.contacts_subtitle),
                modifier = Modifier.absoluteOffset(x = 24.dp, y = 54.dp)
                    .fillMaxWidth().padding(end = 48.dp)
                    .testTag("contacts_subtitle"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(body.fontWeight),
                    fontSize = body.fontSizeSp.sp,
                    lineHeight = body.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
            )
        }
    }
}

@Composable
private fun BoxScope.GrantedContactsContent(
    contacts: List<RasvenContact>,
    query: String,
    contentWidth: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onQueryChange: (String) -> Unit,
    contactPrefix: String,
    onStartOutgoingCall: (String) -> Unit,
    onStartOutgoingContactCall: ((RasvenContact, String) -> Unit)?,
) {
    val colors = RasvenDesignTheme.colors
    val body = RasvenTypographyTokens.bodyS

    RasvenTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.contacts_search_label),
        placeholder = stringResource(R.string.contacts_search_hint),
        appearance = RasvenTextFieldAppearance.Glass,
        // Owner-authorized Android one-step-up override: Figma Medium -> Android Large (48dp).
        size = RasvenTextFieldSize.Large,
        fontFamily = persianFontFamily,
        valueFontFamily = persianFontFamily,
        layoutDirection = productUiDirection(),
        valueTextAlign = productTextAlignment(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        inputModifier = Modifier.testTag("contacts_search_input"),
        modifier = Modifier.absoluteOffset(y = 98.dp).align(Alignment.TopCenter)
            .width(contentWidth).testTag("contacts_search_field"),
    )

    BasicText(
        text = stringResource(R.string.contacts_prefix_guidance),
        modifier = Modifier.absoluteOffset(y = 200.dp).align(Alignment.TopCenter)
            .width(contentWidth).testTag("contacts_prefix_guidance"),
        style = TextStyle(
            color = colors[RasvenSemanticColors.textSecondary],
            fontFamily = persianFontFamily,
            fontWeight = FontWeight(body.fontWeight),
            fontSize = body.fontSizeSp.sp,
            lineHeight = body.lineHeightSp.sp,
            textAlign = productTextAlignment(),
            textDirection = productTextDirection(),
        ),
    )

    LazyColumn(
        modifier = Modifier.absoluteOffset(y = 262.dp).align(Alignment.TopCenter)
            .width(contentWidth).height(dailyRemainingContentHeight(262.dp).coerceAtMost(360.dp)).testTag("contacts_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(contacts, key = { "${it.displayName}\u0000${it.number}" }) { contact ->
            ContactRow(contact, persianFontFamily, englishFontFamily) {
                val destination = prepareContactDialDestination(contact.number, contactPrefix)
                if (onStartOutgoingContactCall != null) onStartOutgoingContactCall(contact, destination)
                else onStartOutgoingCall(destination)
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: RasvenContact,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onClick: () -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val semanticLabel = listOf(contact.displayName, contact.number).joinToString(productSemanticSeparator())
    val nameToken = RasvenTypographyTokens.bodyM
    val numberToken = RasvenTypographyTokens.bodyS
    Column(
        modifier = Modifier.fillMaxWidth().height(72.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = semanticLabel }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("contact_row_${contact.number}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        BasicText(
            text = contact.displayName,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(nameToken.fontWeight),
                fontSize = nameToken.fontSizeSp.sp,
                lineHeight = nameToken.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
            maxLines = 1,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicText(
                text = contact.number,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = englishFontFamily,
                    fontWeight = FontWeight(numberToken.fontWeight),
                    fontSize = numberToken.fontSizeSp.sp,
                    lineHeight = numberToken.lineHeightSp.sp,
                    textDirection = TextDirection.Ltr,
                    textAlign = productTextAlignment(),
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.PermissionRequiredContent(
    contentWidth: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    onRequestPermission: () -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val heading = RasvenTypographyTokens.headingS
    val body = RasvenTypographyTokens.bodyS

    Column(
        modifier = Modifier.absoluteOffset(y = 136.dp).align(Alignment.TopCenter)
            .widthIn(max = contentWidth).wrapContentWidth().heightIn(min = 0.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("contacts_permission_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        BasicText(
            text = stringResource(R.string.contacts_permission_title),
            modifier = Modifier.wrapContentWidth().testTag("contacts_permission_title"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(heading.fontWeight),
                fontSize = heading.fontSizeSp.sp,
                lineHeight = heading.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
        )
        BasicText(
            text = stringResource(R.string.contacts_permission_body),
            modifier = Modifier.widthIn(max = contentWidth - 32.dp).wrapContentWidth().testTag("contacts_permission_body"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textSecondary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(body.fontWeight),
                fontSize = body.fontSizeSp.sp,
                lineHeight = body.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
        )
    }

    RasvenButton(
        label = stringResource(R.string.contacts_permission_cta),
        size = RasvenButtonSize.Large,
        fontFamily = persianFontFamily,
        onClick = onRequestPermission,
        expandToFullWidth = true,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp + dailyBottomLift())
            .width(contentWidth).testTag("contacts_permission_cta"),
    )
}
