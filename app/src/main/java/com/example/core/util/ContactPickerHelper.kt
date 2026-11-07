package com.example.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(
    val name: String,
    val phoneNumber: String
)

object ContactPickerHelper {

    /**
     * Creates an Intent to pick a phone contact directly from the device's contacts app.
     * Directing to CommonDataKinds.Phone ensures that if a contact has multiple numbers,
     * the system picker lets the user pick the specific number.
     */
    fun createPickContactIntent(): Intent {
        return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    }

    /**
     * Resolves the returned contact Uri into a [PickedContact] containing name and clean phone number.
     * Supports both CommonDataKinds.Phone and Contacts URIs.
     */
    fun resolveContact(context: Context, contactUri: Uri): PickedContact? {
        val contentResolver = context.contentResolver

        try {
            // First attempt: treat URI as CommonDataKinds.Phone
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                    val rawNumber = if (numIdx != -1) cursor.getString(numIdx) else ""

                    if (!rawNumber.isNullOrBlank()) {
                        return PickedContact(
                            name = name?.trim().orEmpty(),
                            phoneNumber = cleanPhoneNumber(rawNumber)
                        )
                    }
                }
            }

            // Fallback attempt: if URI was from Contacts table, query Phone table by CONTACT_ID
            contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

                    val contactId = if (idIdx != -1) cursor.getString(idIdx) else null
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""

                    if (contactId != null) {
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val rawNumber = if (numIdx != -1) phoneCursor.getString(numIdx) else ""
                                return PickedContact(
                                    name = name?.trim().orEmpty(),
                                    phoneNumber = cleanPhoneNumber(rawNumber)
                                )
                            }
                        }
                    }

                    if (!name.isNullOrBlank()) {
                        return PickedContact(
                            name = name.trim(),
                            phoneNumber = ""
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Cleans up formatting artifacts from phone numbers (e.g. spaces, brackets, dashes)
     * while retaining the leading '+' for country codes.
     */
    fun cleanPhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }
}

