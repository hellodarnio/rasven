package com.rasven.android.product.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** Minimal read-only Android Contacts projection. No address-book data is logged or persisted. */
internal class AndroidContactsRepository(
    private val context: Context,
) : RasvenContactsRepository {
    override fun loadContacts(): List<RasvenContact> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val contacts = LinkedHashMap<String, RasvenContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex)?.trim().orEmpty() else ""
                if (number.isEmpty()) continue
                val name = if (nameIndex >= 0) cursor.getString(nameIndex)?.trim().orEmpty() else ""
                val displayName = name.ifEmpty { number }
                contacts.putIfAbsent("$displayName\u0000$number", RasvenContact(displayName, number))
            }
        }
        return contacts.values.toList()
    }
}
