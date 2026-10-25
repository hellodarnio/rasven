package com.rasven.android.app

import com.rasven.android.R
import com.rasven.android.designsystem.foundation.rasvenString
import com.rasven.android.designsystem.foundation.RasvenAppLanguageRepository
import com.rasven.android.designsystem.foundation.RasvenGatedProductLanguageScope
import com.rasven.android.designsystem.foundation.RasvenLanguage
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.setup.RasvenAccountInformationScreen
import com.rasven.android.product.setup.RasvenCallSettingsScreen
import com.rasven.android.product.setup.RasvenConnectingScreen
import com.rasven.android.product.setup.RasvenPermissionsEducationScreen
import com.rasven.android.product.setup.RasvenRegistrationFailureScreen
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationSuccessScreen
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenSetupWelcomeScreen
import com.rasven.android.product.setup.RasvenWelcomeCompletionRepository
import com.rasven.android.product.setup.RasvenValidatedAccount
import com.rasven.android.product.setup.RasvenSecureSetupRepository
import com.rasven.android.product.home.RasvenHomeDialerScreen
import com.rasven.android.product.home.RasvenHomeDialerUiState
import com.rasven.android.product.home.RasvenHomeDialerViewModel
import com.rasven.android.product.contacts.AndroidContactsRepository
import com.rasven.android.product.contacts.RasvenContactsPermissionAction
import com.rasven.android.product.contacts.RasvenContactsScreen
import com.rasven.android.product.contacts.RasvenContactsViewModel
import com.rasven.android.product.contacts.contactsPermissionAction
import com.rasven.android.product.call.RasvenOutgoingCallOrigin
import com.rasven.android.product.call.RasvenCallDomain
import com.rasven.android.product.call.RasvenIncomingCallState
import com.rasven.android.product.call.RasvenIncomingCallScreen
import com.rasven.android.product.call.RasvenActiveCallScreen
import com.rasven.android.product.call.RasvenActiveCallViewModel
import com.rasven.android.product.call.RasvenOutgoingCallScreen
import com.rasven.android.product.call.RasvenOutgoingCallState
import com.rasven.android.product.call.RasvenCallEndSummaryScreen
import com.rasven.android.product.call.RasvenAndroidTransferProfileRepository
import com.rasven.android.product.call.RasvenTransferViewModel
import com.rasven.android.product.call.RasvenTransferCallContext
import com.rasven.android.product.call.RasvenTransferMode
import com.rasven.android.product.call.RasvenTransferUiState
import com.rasven.android.product.call.RasvenTransferScreen
import com.rasven.android.product.call.RasvenTransferDtmfSender
import com.rasven.android.product.call.RasvenTransferCommandResult
import com.rasven.android.product.call.RasvenPbxTransferProfileSource
import com.rasven.android.iax2.RasvenIax2Runtime
import com.rasven.android.product.call.RasvenOutgoingCallViewModel
import com.rasven.android.product.history.RasvenHistoryScreen
import com.rasven.android.product.history.RasvenHistoryViewModel
import com.rasven.android.product.settings.RasvenCallNotificationSettingsPlatform
import com.rasven.android.product.settings.RasvenRequiredCallAccess
import com.rasven.android.product.settings.RasvenSettingsScreen
import com.rasven.android.product.settings.RasvenSettingsViewModel
import java.time.ZoneId

/**
 * Product host through Stage5B6. Setup/registration remains retained; Home, Contacts, History, and
 * Settings are real Bottom Navigation destinations. Settings owns its internal sub-pages without
 * duplicating Contacts prefix or registration account truth.
 */
class BootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyIncomingWindowPolicy(intent)
        RasvenIax2Runtime.initialize(applicationContext)
        RasvenCallDomain.initialize(applicationContext)
        processIncomingIntent(intent)

        val setupRepository = RasvenSecureSetupRepository(applicationContext)
        val welcomeRepository = RasvenWelcomeCompletionRepository(applicationContext)
        val savedSetup = setupRepository.load()
        val requiredAccessReadyAtLaunch = RasvenRequiredCallAccess.status(applicationContext).canConnect
        val connectionPreferences = getSharedPreferences("rasven_user_connection_v1", MODE_PRIVATE)
        val connectionPausedAtLaunch = connectionPreferences.getBoolean("paused", false)
        val registrationViewModel = ViewModelProvider(this)[RasvenRegistrationViewModel::class.java]
        registrationViewModel.attachSetupRepository(setupRepository)
        val homeDialerViewModel = ViewModelProvider(this)[RasvenHomeDialerViewModel::class.java]
        // Only a fresh owner restores directly to Home. A retained first-run owner must keep its
        // Connecting/Failure/Success surface across recreation even though credentials are saved.
        if (savedSetup != null && (requiredAccessReadyAtLaunch || connectionPausedAtLaunch) &&
            registrationViewModel.state is RasvenRegistrationState.Idle) {
            homeDialerViewModel.enterHome(savedSetup.account.username)
        }
        homeDialerViewModel.setDoNotDisturb(RasvenCallDomain.isDoNotDisturbEnabled())
        val contactsViewModel = ViewModelProvider(this)[RasvenContactsViewModel::class.java]
        if (savedSetup != null) contactsViewModel.updateContactPrefix(savedSetup.contactPrefix)
        registrationViewModel.restoreSavedAccountOnce(
            savedSetup?.account,
            autoConnect = !connectionPausedAtLaunch && requiredAccessReadyAtLaunch,
        )
        val historyViewModel = ViewModelProvider(this)[RasvenHistoryViewModel::class.java]
        RasvenCallDomain.setHistoryChangedListener(historyViewModel::refresh)
        val settingsViewModel = ViewModelProvider(this)[RasvenSettingsViewModel::class.java]
        val transferProfileRepository = RasvenAndroidTransferProfileRepository(applicationContext)
        RasvenPbxTransferProfileSource.repository = transferProfileRepository
        val transferViewModel = ViewModelProvider(this)[RasvenTransferViewModel::class.java]
        // Recents removal must not replace the UI truth of a still-live outgoing call.
        val outgoingCallViewModel = com.rasven.android.product.call.RasvenProcessOutgoingCall.current()
        val activeCallViewModel = ViewModelProvider(this)[RasvenActiveCallViewModel::class.java]
        RasvenCallDomain.setHoldChangedListener(activeCallViewModel::observeHeld)
        val contactsRepository = AndroidContactsRepository(applicationContext)

        setContent {
            val languageComplete = remember { RasvenAppLanguageRepository.englishComplete(this@BootstrapActivity) }
            var appLanguage by remember { mutableStateOf(RasvenAppLanguageRepository.current(this@BootstrapActivity)) }
            val selectLanguage: ((RasvenLanguage) -> Boolean)? = if (languageComplete) ({ requested ->
                val saved = RasvenAppLanguageRepository.select(this@BootstrapActivity, requested)
                if (saved) appLanguage = requested
                else Toast.makeText(this@BootstrapActivity, rasvenString(R.string.system_language_save_failed), Toast.LENGTH_SHORT).show()
                saved
            }) else null
            RasvenGatedProductLanguageScope(appLanguage, languageComplete) {
            RasvenTheme {
                var showingAccountInformation by remember { mutableStateOf(welcomeRepository.isCompleted()) }
                var pendingAccount by remember {
                    mutableStateOf(savedSetup?.account?.takeIf { !requiredAccessReadyAtLaunch && !connectionPausedAtLaunch })
                }
                var showingPermissionsEducation by remember {
                    mutableStateOf(savedSetup != null && !requiredAccessReadyAtLaunch && !connectionPausedAtLaunch)
                }
                val validatedAccount = pendingAccount
                val registrationState = registrationViewModel.state
                val liveIncomingCallState = RasvenCallDomain.incomingState
                val liveOutgoingCallState = outgoingCallViewModel.state
                val transferDtmfSender = remember {
                    RasvenTransferDtmfSender { sessionId, digit -> RasvenIax2Runtime.sendDtmf(sessionId, digit) }
                }
                LaunchedEffect(liveIncomingCallState, liveOutgoingCallState) {
                    when (val incoming = liveIncomingCallState) {
                        is RasvenIncomingCallState.Active -> transferViewModel.observePeerHeld(incoming.call.sessionId, incoming.call.peerHeld)
                        is RasvenIncomingCallState.Ended -> transferViewModel.observeCallEnded(incoming.summary)
                        else -> Unit
                    }
                    when (val outgoing = liveOutgoingCallState) {
                        is RasvenOutgoingCallState.ConnectedBoundary -> transferViewModel.observePeerHeld(outgoing.session.opaqueId, outgoing.peerHeld)
                        is RasvenOutgoingCallState.Ended -> transferViewModel.observeCallEnded(outgoing.summary)
                        else -> Unit
                    }
                }
                var contactsPermissionRevision by remember { mutableStateOf(0) }
                val contactsPermissionGranted = contactsPermissionRevision.let {
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                }
                val contactsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    contactsViewModel.finishPermissionRecovery()
                    contactsPermissionRevision += 1
                    if (granted) contactsViewModel.loadContacts(contactsRepository)
                    else contactsViewModel.clearContactsForMissingPermission()
                }
                val contactsSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    contactsViewModel.finishPermissionRecovery()
                    contactsPermissionRevision += 1
                    if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        contactsViewModel.loadContacts(contactsRepository)
                    } else {
                        contactsViewModel.clearContactsForMissingPermission()
                    }
                }
                var notificationSettingsRevision by remember { mutableStateOf(0) }
                val notificationSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { notificationSettingsRevision += 1 }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) notificationSettingsRevision += 1
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val notificationsEnabled = remember(notificationSettingsRevision) {
                    RasvenCallNotificationSettingsPlatform.notificationsEnabled(this@BootstrapActivity)
                }
                var microphonePermissionRevision by remember { mutableStateOf(0) }
                val microphonePermissionGranted = microphonePermissionRevision.let {
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                }
                val requiredAccess = remember(notificationSettingsRevision, microphonePermissionRevision) {
                    RasvenRequiredCallAccess.status(this@BootstrapActivity)
                }
                var requiredAccessAttempted by rememberSaveable { mutableStateOf(false) }
                val continueSetup = {
                    if (!RasvenRequiredCallAccess.status(this@BootstrapActivity).canConnect) {
                        showingPermissionsEducation = true
                        requiredAccessAttempted = true
                    } else {
                        validatedAccount?.let { account ->
                            if (setupRepository.save(account, contactsViewModel.contactPrefix)) {
                                when (registrationViewModel.state) {
                                    is RasvenRegistrationState.Idle -> registrationViewModel.startOnce(account)
                                    is RasvenRegistrationState.Unregistered -> {
                                        homeDialerViewModel.enterHome(account.username)
                                        registrationViewModel.reconnectFromHome()
                                    }
                                    is RasvenRegistrationState.Failed -> registrationViewModel.retryFailedAttempt()
                                    else -> Unit
                                }
                            } else {
                                Toast.makeText(this@BootstrapActivity, rasvenString(R.string.system_account_save_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Unit
                }
                val microphonePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    microphonePermissionRevision += 1
                    outgoingCallViewModel.finishMicrophonePermissionAction(granted)
                }
                val onboardingCallSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    notificationSettingsRevision += 1
                    continueSetup()
                }
                val onboardingPermissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ ->
                    notificationSettingsRevision += 1
                    microphonePermissionRevision += 1
                    requiredAccessAttempted = true
                    val current = RasvenRequiredCallAccess.status(this@BootstrapActivity)
                    if (current.canConnect) continueSetup()
                    else if (current.microphoneGranted) {
                        RasvenCallNotificationSettingsPlatform.resolveIntent(this@BootstrapActivity)?.let {
                            onboardingCallSettingsLauncher.launch(it)
                        }
                    }
                }
                val requestRequiredAccess: () -> Unit = {
                    val current = RasvenRequiredCallAccess.status(this@BootstrapActivity)
                    if (current.canConnect) continueSetup()
                    else if (!current.microphoneGranted && requiredAccessAttempted &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        requiredAccessAttempted = true
                        onboardingCallSettingsLauncher.launch(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ))
                    } else {
                        val missing = buildList {
                            if (!current.microphoneGranted) add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (missing.isNotEmpty()) {
                            requiredAccessAttempted = true
                            onboardingPermissionsLauncher.launch(missing.toTypedArray())
                        } else {
                            RasvenCallNotificationSettingsPlatform.resolveIntent(this@BootstrapActivity)?.let {
                                onboardingCallSettingsLauncher.launch(it)
                            } ?: run { requiredAccessAttempted = true }
                        }
                    }
                }
                val openRequiredAccessSettings: () -> Unit = {
                    val current = RasvenRequiredCallAccess.status(this@BootstrapActivity)
                    val settingsIntent = if (!current.microphoneGranted) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    } else {
                        RasvenCallNotificationSettingsPlatform.resolveIntent(this@BootstrapActivity)
                            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    }
                    onboardingCallSettingsLauncher.launch(settingsIntent)
                }
                var incomingPermissionCallId by rememberSaveable { mutableStateOf<Long?>(null) }
                var incomingMicrophoneRequested by rememberSaveable { mutableStateOf(false) }
                val incomingPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    incomingPermissionCallId?.let { RasvenCallDomain.onMicrophonePermissionResult(it, granted) }
                    incomingPermissionCallId = null
                }
                val incomingMicrophoneSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    incomingPermissionCallId?.let { id ->
                        RasvenCallDomain.onMicrophonePermissionResult(
                            id,
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                        )
                    }
                    incomingPermissionCallId = null
                }
                val requestIncomingMicrophone: (Long) -> Unit = { callId ->
                    incomingPermissionCallId = callId
                    val permanentlyDenied = incomingMicrophoneRequested &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                    if (permanentlyDenied) {
                        incomingMicrophoneSettingsLauncher.launch(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ))
                    } else {
                        incomingMicrophoneRequested = true
                        incomingPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                val microphoneSettingsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    microphonePermissionRevision += 1
                    outgoingCallViewModel.finishMicrophonePermissionAction(
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val requestMicrophoneAccess = {
                    if (outgoingCallViewModel.beginMicrophonePermissionAction()) {
                        val permanentlyDenied = outgoingCallViewModel.hasRequestedMicrophonePermission &&
                            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                        if (permanentlyDenied) {
                            microphoneSettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                ),
                            )
                        } else {
                            outgoingCallViewModel.markMicrophonePermissionRequestIssued()
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                val startHomeOutgoingCall: (String) -> Unit = { destination ->
                    outgoingCallViewModel.requestStart(
                        destination = destination,
                        origin = RasvenOutgoingCallOrigin.HomeDirectDial,
                        microphoneGranted = microphonePermissionGranted,
                        registrationState = registrationState,
                    )
                }
                val startContactOutgoingCall: (String) -> Unit = { destination ->
                    outgoingCallViewModel.requestStart(
                        destination = destination,
                        origin = RasvenOutgoingCallOrigin.Contacts,
                        microphoneGranted = microphonePermissionGranted,
                        registrationState = registrationState,
                    )
                }
                val requestContactsAccess = {
                    if (contactsViewModel.beginPermissionRecovery()) {
                        when (contactsPermissionAction(
                            hasRequestedBefore = contactsViewModel.hasRequestedPermission,
                            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS),
                        )) {
                            RasvenContactsPermissionAction.RequestSystemPermission -> {
                                contactsViewModel.markPermissionRequestIssued()
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                            RasvenContactsPermissionAction.OpenAppSettings -> {
                                contactsSettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null),
                                    ),
                                )
                            }
                        }
                    }
                }
                val navigateProductDestination: (RasvenNavDestination) -> Unit = { destination ->
                    if (destination != RasvenNavDestination.Settings) settingsViewModel.openMain()
                    when (destination) {
                        RasvenNavDestination.Home -> homeDialerViewModel.navigateTo(RasvenNavDestination.Home)
                        RasvenNavDestination.Contacts -> {
                            if (contactsPermissionGranted) contactsViewModel.loadContacts(contactsRepository)
                            else contactsViewModel.clearContactsForMissingPermission()
                            homeDialerViewModel.navigateTo(RasvenNavDestination.Contacts)
                        }
                        RasvenNavDestination.History -> homeDialerViewModel.navigateTo(RasvenNavDestination.History)
                        RasvenNavDestination.Settings -> {
                            settingsViewModel.openMain()
                            homeDialerViewModel.navigateTo(RasvenNavDestination.Settings)
                        }
                    }
                }

                val returnToCall = {
                    if (!transferViewModel.returnToActiveCall()) transferViewModel.minimizeToActiveCall()
                    Unit
                }
                val openTransfer = { context: RasvenTransferCallContext ->
                    if (transferViewModel.state is RasvenTransferUiState.Idle) transferViewModel.begin(context)
                    else transferViewModel.reopen()
                }
                when {
                    transferViewModel.state !is RasvenTransferUiState.Idle && !transferViewModel.minimized -> {
                        RasvenTransferScreen(
                            state = transferViewModel.state,
                            fontFamily = RasvenAndroidFontFamilies.Persian,
                            englishFontFamily = FontFamily.Default,
                            configuredDestinations = transferViewModel.configuredDestinations(),
                            onChooseMode = transferViewModel::chooseMode,
                            onEnterManual = transferViewModel::enterManualDestination,
                            onManualChanged = transferViewModel::editManualDestination,
                            onSubmitManual = { transferViewModel.submitManualDestination(transferDtmfSender) },
                            onSelectDestination = { transferViewModel.selectConfiguredDestination(it, transferDtmfSender) },
                            onContinueAttendedDestination = { transferViewModel.continueAttendedDestination(transferDtmfSender) },
                            onConfirmConsultation = transferViewModel::confirmConsultationConnected,
                            onRequestComplete = transferViewModel::requestAttendedCompletion,
                            onConfirmComplete = {
                                when (transferViewModel.confirmAttendedCompletion(transferDtmfSender)) {
                                    RasvenTransferCommandResult.HangupRequired -> {
                                        val call = transferViewModel.activeCallContext()
                                        val ended = when {
                                            call == null -> false
                                            call.outgoing -> outgoingCallViewModel.cancel()
                                            else -> RasvenCallDomain.requestEnd(call.sessionId)
                                        }
                                        call?.let { transferViewModel.confirmHangupAccepted(it.sessionId, ended) }
                                    }
                                    else -> Unit
                                }
                            },
                            onCancelAttended = { transferViewModel.cancelAttendedTransfer(transferDtmfSender) },
                            canCancelAttended = transferProfileRepository.load().cancelAttendedCode.isNotEmpty(),
                            onBackFromCompletion = transferViewModel::backFromAttendedCompletion,
                            onReportBusy = { transferViewModel.activeCallContext()?.let { transferViewModel.reportAttendedDestinationBusy(it.sessionId) } },
                            onReportNoAnswer = { transferViewModel.activeCallContext()?.let { transferViewModel.reportAttendedNoAnswer(it.sessionId) } },
                            onReportBlindStillActive = { transferViewModel.activeCallContext()?.let { transferViewModel.reportBlindOriginalStillActive(it.sessionId) } },
                            onReturnToCall = returnToCall,
                            onDismiss = transferViewModel::dismissResult,
                            onBackToMode = transferViewModel::backToModeSelection,
                        )
                    }
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Ended -> {
                        RasvenCallEndSummaryScreen(
                            summary = (RasvenCallDomain.incomingState as RasvenIncomingCallState.Ended).summary,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onReturnToPrevious = RasvenCallDomain::closeCallEndSummary,
                        )
                    }
                    outgoingCallViewModel.state is RasvenOutgoingCallState.Ended -> {
                        RasvenCallEndSummaryScreen(
                            summary = (outgoingCallViewModel.state as RasvenOutgoingCallState.Ended).summary,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onReturnToPrevious = outgoingCallViewModel::closeCallEndSummary,
                        )
                    }
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Active -> {
                        val active = (RasvenCallDomain.incomingState as RasvenIncomingCallState.Active).call
                        activeCallViewModel.bind(active.sessionId)
                        activeCallViewModel.observeHeld(active.sessionId, RasvenCallDomain.isHeld(active.sessionId))
                        DisposableEffect(active.sessionId) {
                            val observation = RasvenCallDomain.observeSpeaker(active.sessionId, activeCallViewModel::setSpeaker)
                            onDispose { observation.close() }
                        }
                        RasvenActiveCallScreen(
                            destination = active.name ?: active.number,
                            lineIdentity = active.number,
                            connectedAtElapsedMillis = active.connectedAtElapsedMillis ?: (System.nanoTime() / 1_000_000L),
                            controls = activeCallViewModel.controls,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onMute = activeCallViewModel::setMuted,
                            canHold = true,
                            onHold = activeCallViewModel::setHeld,
                            onSpeaker = { desired ->
                                if (RasvenCallDomain.requestSpeaker(active.sessionId, desired)) activeCallViewModel.setSpeaker(desired)
                            },
                            onKeypad = activeCallViewModel::toggleKeypad,
                            onDtmf = activeCallViewModel::sendDtmf,
                            onEnd = {
                                RasvenCallDomain.requestEnd(active.sessionId)
                                activeCallViewModel.clearForSession(active.sessionId)
                            },
                            onTransfer = {
                                openTransfer(RasvenTransferCallContext(
                                    sessionId = active.sessionId,
                                    displayIdentity = active.name ?: active.number,
                                    connectedAtElapsedMillis = active.connectedAtElapsedMillis ?: (System.nanoTime() / 1_000_000L),
                                    outgoing = false,
                                    manuallyHeld = activeCallViewModel.controls.held || active.peerHeld,
                                ))
                            },
                        )
                    }
                    outgoingCallViewModel.state is RasvenOutgoingCallState.ConnectedBoundary -> {
                        val active = outgoingCallViewModel.state as RasvenOutgoingCallState.ConnectedBoundary
                        activeCallViewModel.bind(active.session.opaqueId)
                        activeCallViewModel.observeHeld(active.session.opaqueId, RasvenCallDomain.isHeld(active.session.opaqueId))
                        DisposableEffect(active.session.opaqueId) {
                            val observation = RasvenCallDomain.observeSpeaker(active.session.opaqueId, activeCallViewModel::setSpeaker)
                            onDispose { observation.close() }
                        }
                        RasvenActiveCallScreen(
                            destination = active.attempt.displayIdentity ?: active.attempt.destination,
                            lineIdentity = active.attempt.destination,
                            connectedAtElapsedMillis = active.session.connectedAtElapsedMillis,
                            controls = activeCallViewModel.controls,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onMute = activeCallViewModel::setMuted,
                            canHold = true,
                            onHold = activeCallViewModel::setHeld,
                            onSpeaker = { desired ->
                                if (RasvenCallDomain.requestSpeaker(active.session.opaqueId, desired)) activeCallViewModel.setSpeaker(desired)
                            },
                            onKeypad = activeCallViewModel::toggleKeypad,
                            onDtmf = activeCallViewModel::sendDtmf,
                            onEnd = {
                                outgoingCallViewModel.cancel()
                                activeCallViewModel.clearForSession(active.session.opaqueId)
                            },
                            onTransfer = {
                                openTransfer(RasvenTransferCallContext(
                                    sessionId = active.session.opaqueId,
                                    displayIdentity = active.attempt.displayIdentity ?: active.attempt.destination,
                                    connectedAtElapsedMillis = active.session.connectedAtElapsedMillis,
                                    outgoing = true,
                                    manuallyHeld = activeCallViewModel.controls.held || active.peerHeld,
                                ))
                            },
                        )
                    }
                    RasvenCallDomain.incomingState !is RasvenIncomingCallState.Idle -> {
                        RasvenIncomingCallScreen(
                            state = RasvenCallDomain.incomingState,
                            locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onAnswer = { RasvenCallDomain.requestAnswer(it) },
                            onReject = { RasvenCallDomain.requestReject(it) },
                            onEnd = { RasvenCallDomain.requestEnd(it) },
                            onRequestMicrophone = requestIncomingMicrophone,
                            onClose = RasvenCallDomain::closeMissedSurface,
                            onCallback = { destination ->
                                RasvenCallDomain.closeMissedSurface()
                                outgoingCallViewModel.requestStart(
                                    destination = destination,
                                    origin = RasvenOutgoingCallOrigin.HistoryCallback,
                                    microphoneGranted = microphonePermissionGranted,
                                    registrationState = registrationState,
                                )
                            },
                        )
                    }
                    outgoingCallViewModel.state is RasvenOutgoingCallState.AwaitingMicrophonePermission ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.Dialing ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.Ringing ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.Busy ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.Rejected ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.NoAnswer ||
                        outgoingCallViewModel.state is RasvenOutgoingCallState.NetworkError -> {
                        RasvenOutgoingCallScreen(
                            state = outgoingCallViewModel.state,
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onPermissionRequest = requestMicrophoneAccess,
                            onClosePermission = outgoingCallViewModel::closePermissionSurface,
                            onCancel = {
                                if (outgoingCallViewModel.cancel()) {
                                    outgoingCallViewModel.closeCancelled()
                                }
                            },
                            onRetry = {
                                outgoingCallViewModel.retry(
                                    microphoneGranted = microphonePermissionGranted,
                                    registrationState = registrationState,
                                )
                            },
                            onCloseFailure = outgoingCallViewModel::closeFailureSurface,
                        )
                    }
                    homeDialerViewModel.localState.active && (
                        registrationState is RasvenRegistrationState.Registered ||
                            registrationState is RasvenRegistrationState.Unregistered ||
                            registrationState is RasvenRegistrationState.Registering
                    ) -> {
                        val homeState = homeDialerViewModel.localState
                        when (homeDialerViewModel.currentDestination) {
                            RasvenNavDestination.Contacts -> {
                                RasvenContactsScreen(
                                    permissionGranted = contactsPermissionGranted,
                                    contacts = contactsViewModel.visibleContacts,
                                    query = contactsViewModel.query,
                                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                                    // QA_LATIN_FONT_FALLBACK: Latin mapping unchanged; technical values remain LTR.
                                    englishFontFamily = FontFamily.Default,
                                    onQueryChange = contactsViewModel::updateQuery,
                                    onRequestPermission = requestContactsAccess,
                                    contactPrefix = contactsViewModel.contactPrefix,
                                    onStartOutgoingCall = startContactOutgoingCall,
                                    onNavigationDestination = navigateProductDestination,
                                    onStartOutgoingContactCall = { contact, destination ->
                                        outgoingCallViewModel.requestStart(
                                            destination = destination,
                                            origin = RasvenOutgoingCallOrigin.Contacts,
                                            microphoneGranted = microphonePermissionGranted,
                                            registrationState = registrationState,
                                            displayIdentity = contact.displayName,
                                        )
                                    },
                                )
                            }
                            RasvenNavDestination.History -> {
                                val historyState = historyViewModel.uiState
                                RasvenHistoryScreen(
                                    state = historyState,
                                    nowEpochMillis = System.currentTimeMillis(),
                                    zoneId = ZoneId.systemDefault(),
                                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                                    englishFontFamily = FontFamily.Default,
                                    onFilterSelected = historyViewModel::selectFilter,
                                    onNavigationDestination = navigateProductDestination,
                                )
                            }
                            RasvenNavDestination.Settings -> {
                                val settingsState = settingsViewModel.uiState
                                RasvenSettingsScreen(
                                    state = settingsState,
                                    currentContactPrefix = contactsViewModel.contactPrefix,
                                    notificationsEnabled = notificationsEnabled,
                                    advancedAvailable = registrationViewModel.currentSettingsSnapshot() != null,
                                    connectionState = registrationState,
                                    advancedErrors = settingsViewModel.advancedErrors(),
                                    transferProfileErrors = settingsViewModel.transferProfileErrors(),
                                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                                    englishFontFamily = FontFamily.Default,
                                    onOpenContactPrefix = {
                                        settingsViewModel.openContactPrefix(contactsViewModel.contactPrefix)
                                    },
                                    onSelectLanguage = selectLanguage,
                                    onContactPrefixChanged = settingsViewModel::updateContactPrefixDraft,
                                    onSaveContactPrefix = {
                                        settingsViewModel.saveContactPrefix { prefix ->
                                            if (setupRepository.saveContactPrefix(prefix)) contactsViewModel.updateContactPrefix(prefix)
                                            else Toast.makeText(this@BootstrapActivity, rasvenString(R.string.system_settings_save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenCallNotifications = settingsViewModel::openCallNotifications,
                                    onOpenSystemCallSettings = {
                                        RasvenCallNotificationSettingsPlatform.resolveIntent(this@BootstrapActivity)
                                            ?.let(notificationSettingsLauncher::launch)
                                    },
                                    onOpenAdvanced = {
                                        registrationViewModel.currentSettingsSnapshot()?.let { account ->
                                            settingsViewModel.openAdvanced(account, transferProfileRepository.load())
                                        }
                                    },
                                    onDisconnect = {
                                        if (registrationViewModel.disconnectFromSettings { releaseConfirmed ->
                                                if (!releaseConfirmed) {
                                                    Toast.makeText(
                                                        this@BootstrapActivity,
                                                        rasvenString(R.string.system_disconnect_unconfirmed),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }) {
                                            connectionPreferences.edit().putBoolean("paused", true).apply()
                                            settingsViewModel.openMain()
                                            homeDialerViewModel.navigateTo(RasvenNavDestination.Home)
                                        }
                                    },
                                    onAdvancedServerChanged = settingsViewModel::updateAdvancedServer,
                                    onAdvancedPortChanged = settingsViewModel::updateAdvancedPort,
                                    onAdvancedUsernameChanged = settingsViewModel::updateAdvancedUsername,
                                    onAdvancedPasswordChanged = settingsViewModel::updateAdvancedPasswordReplacement,
                                    onTransferProfileChanged = settingsViewModel::updateTransferProfile,
                                    onRestoreTransferDefaults = settingsViewModel::restoreTransferProfileDefaults,
                                    onSaveAdvanced = {
                                        val profile = settingsViewModel.validatedTransferProfile()
                                        val update = settingsViewModel.validatedAdvancedUpdate()
                                        if (profile != null && update != null && registrationViewModel.applySettingsUpdate(update)) {
                                            // Do not show a successful save when the PBX feature map was not persisted.
                                            // Otherwise transfer can silently keep using the previous codes.
                                            if (transferProfileRepository.save(profile)) {
                                                settingsViewModel.completeAdvancedSave()
                                            } else {
                                                Toast.makeText(
                                                    this@BootstrapActivity,
                                                    rasvenString(R.string.settings_transfer_save_failed),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                    onBackToMain = settingsViewModel::backToMain,
                                    onNavigationDestination = navigateProductDestination,
                                )
                            }
                            RasvenNavDestination.Home -> {
                                RasvenHomeDialerScreen(
                                    state = RasvenHomeDialerUiState(
                                        connectionState = registrationState,
                                        extension = homeState.extension,
                                        doNotDisturb = homeState.doNotDisturb,
                                        destination = homeState.destination,
                                    ),
                                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                                    englishFontFamily = FontFamily.Default,
                                    onDestinationChange = homeDialerViewModel::setDestination,
                                    onDialKey = homeDialerViewModel::append,
                                    onDelete = homeDialerViewModel::deleteLast,
                                    onDoNotDisturbChanged = { enabled ->
                                        RasvenCallDomain.setDoNotDisturb(enabled)
                                        homeDialerViewModel.setDoNotDisturb(enabled)
                                        Toast.makeText(
                                            this@BootstrapActivity,
                                            if (enabled) rasvenString(R.string.system_dnd_on)
                                            else rasvenString(R.string.system_dnd_off),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    onStartOutgoingCall = startHomeOutgoingCall,
                                    onReconnect = {
                                        if (RasvenRequiredCallAccess.status(this@BootstrapActivity).canConnect) {
                                            connectionPreferences.edit().putBoolean("paused", false).apply()
                                            registrationViewModel.reconnectFromHome()
                                        } else {
                                            connectionPreferences.edit().putBoolean("paused", false).apply()
                                            pendingAccount = savedSetup?.account
                                            showingPermissionsEducation = pendingAccount != null
                                        }
                                    },
                                    onNavigationDestination = navigateProductDestination,
                                )
                            }
                        }
                    }
                    registrationState is RasvenRegistrationState.Registered -> {
                        RasvenRegistrationSuccessScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onEnterDialer = {
                                registrationViewModel.currentUsernameForDisplay()?.let(homeDialerViewModel::enterHome)
                            },
                        )
                    }
                    registrationState is RasvenRegistrationState.Registering -> {
                        RasvenConnectingScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                        )
                    }
                    registrationState is RasvenRegistrationState.Failed -> {
                        RasvenRegistrationFailureScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onRetry = {
                                if (RasvenRequiredCallAccess.status(this@BootstrapActivity).canConnect) {
                                    registrationViewModel.retryFailedAttempt()
                                } else {
                                    pendingAccount = savedSetup?.account
                                    showingPermissionsEducation = pendingAccount != null
                                }
                            },
                            onOpenSettings = {
                                // Figma prototype 766:5917 navigates to 765:5295 (Stage5A2).
                                registrationViewModel.prepareForAccountEditAfterFailure()
                                pendingAccount = null
                                showingPermissionsEducation = false
                                showingAccountInformation = true
                            },
                        )
                    }
                    showingPermissionsEducation && validatedAccount != null -> {
                        RasvenPermissionsEducationScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            requiredAccessReady = requiredAccess.canConnect,
                            onOpenRequiredAccessSettings = openRequiredAccessSettings,
                            onRequestRequiredAccess = requestRequiredAccess,
                        )
                    }
                    validatedAccount != null -> {
                        RasvenCallSettingsScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            englishFontFamily = FontFamily.Default,
                            onSaveAndConnect = { contactPrefix ->
                                contactsViewModel.updateContactPrefix(contactPrefix)
                                showingPermissionsEducation = true
                            },
                        )
                    }
                    showingAccountInformation -> {
                        RasvenAccountInformationScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            englishFontFamily = FontFamily.Default,
                            onContinue = { verifiedAccount -> pendingAccount = verifiedAccount },
                        )
                    }
                    else -> {
                        RasvenSetupWelcomeScreen(
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            englishFontFamily = RasvenAndroidFontFamilies.English,
                            onStartSetup = {
                                if (welcomeRepository.complete()) showingAccountInformation = true
                                else Toast.makeText(this@BootstrapActivity, rasvenString(R.string.welcome_save_failed), Toast.LENGTH_SHORT).show()
                            },
                            onSelectLanguage = { requested -> selectLanguage?.invoke(requested) },
                        )
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIncomingWindowPolicy(intent)
        processIncomingIntent(intent)
    }

    private fun processIncomingIntent(intent: Intent?) {
        if (!com.rasven.android.product.call.RasvenIncomingIntentAuthorization.isAuthorized(applicationContext, intent)) return
        if (intent?.action != com.rasven.android.product.call.RasvenCallNotification.ACTION_ANSWER) return
        val callId = intent.getLongExtra(com.rasven.android.product.call.RasvenCallNotification.EXTRA_CALL_ID, -1L)
        if (callId >= 0L) RasvenCallDomain.prepareAnswerFromForeground(callId)
    }

    private fun applyIncomingWindowPolicy(intent: Intent?) {
        val showIncomingOverLockScreen = com.rasven.android.product.call.RasvenIncomingIntentAuthorization.isAuthorized(applicationContext, intent) && intent?.action in setOf(
            com.rasven.android.product.call.RasvenCallNotification.ACTION_OPEN_INCOMING,
            com.rasven.android.product.call.RasvenCallNotification.ACTION_ANSWER,
        )
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(showIncomingOverLockScreen)
            setTurnScreenOn(showIncomingOverLockScreen)
        } else {
            @Suppress("DEPRECATION")
            if (showIncomingOverLockScreen) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    override fun onDestroy() {
        RasvenCallDomain.setHistoryChangedListener(null)
        RasvenCallDomain.setHoldChangedListener(null)
        super.onDestroy()
    }
}
