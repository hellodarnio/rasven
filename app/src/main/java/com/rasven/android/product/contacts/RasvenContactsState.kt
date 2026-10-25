package com.rasven.android.product.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal data class RasvenContact(
    val displayName: String,
    val number: String,
)

internal fun interface RasvenContactsRepository {
    fun loadContacts(): List<RasvenContact>
}

internal fun filterContacts(contacts: List<RasvenContact>, query: String): List<RasvenContact> {
    val needle = query.trim()
    if (needle.isEmpty()) return contacts
    return contacts.filter { contact ->
        contact.displayName.contains(needle, ignoreCase = true) || contact.number.contains(needle)
    }
}

/**
 * V1 Contacts dialing contract from Android/Product handoff 198:4422.
 * Iranian +98 / 0098 / leading-0 representations collapse to the same national form before the
 * organization prefix is applied. Short/internal destinations stay outside this pipeline.
 */
internal fun normalizeIranContactNumber(raw: String): String {
    val compact = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (isShortOrInternalContactDestination(compact)) return compact

    val digits = compact.filter(Char::isDigit)
    return when {
        compact.startsWith("+98") && digits.startsWith("98") -> "0" + digits.drop(2)
        digits.startsWith("0098") -> "0" + digits.drop(4)
        else -> digits
    }
}

internal fun isShortOrInternalContactDestination(raw: String): Boolean {
    val compact = raw.filter { it.isDigit() || it == '*' || it == '#' }
    val digits = compact.filter(Char::isDigit)
    return compact.isNotEmpty() && compact.none { it == '+' } && digits.length <= 5
}

internal fun prepareContactDialDestination(raw: String, contactPrefix: String): String {
    val compact = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (isShortOrInternalContactDestination(compact)) return compact
    val national = normalizeIranContactNumber(compact)
    val prefix = contactPrefix.filter { it.isDigit() || it == '*' || it == '#' }
    return prefix + national
}

internal enum class RasvenContactsPermissionAction {
    RequestSystemPermission,
    OpenAppSettings,
}

internal fun contactsPermissionAction(
    hasRequestedBefore: Boolean,
    shouldShowRationale: Boolean,
): RasvenContactsPermissionAction =
    if (hasRequestedBefore && !shouldShowRationale) RasvenContactsPermissionAction.OpenAppSettings
    else RasvenContactsPermissionAction.RequestSystemPermission

/** Retained Contacts state. Contact rows stay in-memory only and are never logged or persisted. */
internal class RasvenContactsViewModel : ViewModel() {
    private var allContacts: List<RasvenContact> by mutableStateOf(emptyList())

    var query: String by mutableStateOf("")
        private set

    var contactPrefix: String by mutableStateOf("")
        private set

    var hasRequestedPermission: Boolean by mutableStateOf(false)
        private set

    var permissionRequestInFlight: Boolean by mutableStateOf(false)
        private set

    val visibleContacts: List<RasvenContact>
        get() = filterContacts(allContacts, query)

    fun updateQuery(value: String) {
        query = value
    }

    fun updateContactPrefix(value: String) {
        contactPrefix = value
    }

    fun loadContacts(repository: RasvenContactsRepository) {
        allContacts = repository.loadContacts()
    }

    fun clearContactsForMissingPermission() {
        allContacts = emptyList()
    }

    fun beginPermissionRecovery(): Boolean {
        if (permissionRequestInFlight) return false
        permissionRequestInFlight = true
        return true
    }

    fun markPermissionRequestIssued() {
        hasRequestedPermission = true
    }

    fun finishPermissionRecovery() {
        permissionRequestInFlight = false
    }

    fun prepareSelectedContact(contact: RasvenContact): String =
        prepareContactDialDestination(contact.number, contactPrefix)
}
