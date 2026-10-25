package com.rasven.android.product.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenOutlineButton
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/** Product Figma 766:5633 geometry with permission copy matching the host action. */
internal object RasvenPermissionsEducationContract {
    const val headerToIntroGapDp = 28
    const val introToPermissionsGapDp = 38
    const val introInsetDp = 16
    const val permissionsInsetDp = 12
    const val permissionRowGapDp = 16
    const val actionGapDp = 12
}

/**
 * Stage5A5 explains the call permissions. The host owns the platform request, while this screen
 * keeps the two continuation actions separate and does not itself start registration.
 */
@Composable
internal fun RasvenPermissionsEducationScreen(
    persianFontFamily: FontFamily,
    requiredAccessReady: Boolean,
    onOpenRequiredAccessSettings: () -> Unit,
    onRequestRequiredAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = setupPageFont(persianFontFamily)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp),
    ) {
        val pageMargin = setupPageMargin(maxWidth.value)

        RasvenSetupAmbient()


        CompositionLocalProvider(LocalLayoutDirection provides setupLayoutDirection()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PermissionsEducationHeader(
                    pageMargin = pageMargin,
                    persianFontFamily = pageFont,
                )

                Column(
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = RasvenPermissionsEducationContract.headerToIntroGapDp.dp,
                            start = pageMargin,
                            end = pageMargin,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        )
                        .testTag("permissions_education_content"),
                ) {
                    PermissionsGlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("permissions_intro_card"),
                        insetDp = RasvenPermissionsEducationContract.introInsetDp,
                    ) {
                        val body = RasvenTypographyTokens.bodyS
                        BasicText(
                            text = stringResource(R.string.permissions_intro),
                            style = TextStyle(
                                color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                                fontFamily = pageFont,
                                fontWeight = FontWeight(body.fontWeight),
                                fontSize = body.fontSizeSp.sp,
                                lineHeight = body.lineHeightSp.sp,
                                textAlign = setupTextAlignment(),
                            ),
                        )
                    }

                    Spacer(Modifier.height(RasvenPermissionsEducationContract.introToPermissionsGapDp.dp))

                    PermissionsGlassCard(
                        modifier = Modifier.fillMaxWidth().testTag("permissions_card"),
                        insetDp = RasvenPermissionsEducationContract.permissionsInsetDp,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                RasvenPermissionsEducationContract.permissionRowGapDp.dp,
                            ),
                        ) {
                            PermissionEducationRow(
                                textRes = R.string.permissions_microphone,
                                iconRes = R.drawable.rasven_permission_microphone,
                                fontFamily = pageFont,
                                rowTag = "permissions_microphone_row",
                                iconTag = "permissions_microphone_icon",
                            )
                            PermissionEducationRow(
                                textRes = R.string.permissions_incoming_call,
                                iconRes = R.drawable.rasven_permission_incoming_call,
                                fontFamily = pageFont,
                                rowTag = "permissions_incoming_call_row",
                                iconTag = "permissions_incoming_call_icon",
                            )
                            PermissionEducationRow(
                                textRes = R.string.permissions_contacts,
                                iconRes = R.drawable.rasven_permission_contacts,
                                fontFamily = pageFont,
                                rowTag = "permissions_contacts_row",
                                iconTag = "permissions_contacts_icon",
                            )
                        }
                    }
                    BasicText(
                        text = stringResource(
                            if (requiredAccessReady) R.string.permissions_required_access_ready
                            else R.string.permissions_required_access_missing,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            .testTag("permissions_required_access_status"),
                        style = TextStyle(
                            color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                            fontFamily = pageFont,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            textAlign = setupTextAlignment(),
                        ),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = pageMargin,
                        end = pageMargin,
                        bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(RasvenPermissionsEducationContract.actionGapDp.dp),
                ) {
                    RasvenOutlineButton(
                        label = stringResource(R.string.permissions_open_required_settings),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = onOpenRequiredAccessSettings,
                        modifier = Modifier.fillMaxWidth().testTag("permissions_open_required_settings"),
                        expandToFullWidth = true,
                    )
                    RasvenButton(
                        label = stringResource(R.string.permissions_continue_connect),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = onRequestRequiredAccess,
                        modifier = Modifier.fillMaxWidth().testTag("permissions_continue_connect"),
                        expandToFullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsEducationHeader(
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
) {
    val headingStyle = RasvenTypographyTokens.headingL
    val subtitleStyle = RasvenTypographyTokens.bodyS
    val direction = LocalLayoutDirection.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            top = RasvenSemanticLayout.spacingInsetDefault.dp,
            start = pageMargin,
            end = pageMargin,
        ),
        horizontalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingInlineXl.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingStackXs.dp),
        ) {
            BasicText(
                text = stringResource(R.string.permissions_heading),
                modifier = Modifier.semantics { heading() },
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(headingStyle.fontWeight),
                    fontSize = headingStyle.fontSizeSp.sp,
                    lineHeight = headingStyle.lineHeightSp.sp,
                    textAlign = if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
                ),
            )
            BasicText(
                text = stringResource(R.string.permissions_subheading),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(subtitleStyle.fontWeight),
                    fontSize = subtitleStyle.fontSizeSp.sp,
                    lineHeight = subtitleStyle.lineHeightSp.sp,
                    textAlign = if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
                ),
            )
        }
        Image(
            painter = painterResource(R.drawable.rasven_brand_symbol_v01_candidate),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
    }
}

@Composable
private fun PermissionsGlassCard(
    modifier: Modifier,
    insetDp: Int,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val stroke = RasvenSemanticLayout.borderWidthDefault.dp
    Column(
        modifier = modifier
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.surfaceGlass])
            .border(stroke, RasvenDesignTheme.colors[RasvenSemanticColors.borderGlass], shape)
            .padding(stroke)
            .padding(insetDp.dp),
    ) {
        content()
    }
}

@Composable
private fun PermissionEducationRow(
    textRes: Int,
    iconRes: Int,
    fontFamily: FontFamily,
    rowTag: String,
    iconTag: String,
) {
    val body = RasvenTypographyTokens.bodyM
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = RasvenSemanticLayout.iconSizeLg.dp)
            .testTag(rowTag),
        horizontalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingInlineMd.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(RasvenDesignTheme.colors[RasvenSemanticColors.iconPrimary]),
            modifier = Modifier.size(RasvenSemanticLayout.iconSizeLg.dp).testTag(iconTag),
        )
        BasicText(
            text = stringResource(textRes),
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                fontFamily = fontFamily,
                fontWeight = FontWeight(body.fontWeight),
                fontSize = body.fontSizeSp.sp,
                lineHeight = body.lineHeightSp.sp,
                textAlign = setupTextAlignment(),
            ),
        )
    }
}
