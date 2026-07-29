package com.xconflictionx.callguardshield.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.xconflictionx.callguardshield.logic.PhoneHelper

data class ContactDetails(
    val name: String,
    val photoUri: Uri? = null,
    val lookupKey: String? = null,
    val phoneNumber: String? = null
)

class ContactRepository(private val context: Context) {

    /**
     * Finds a contact name and photo by phone number.
     */
    fun getContactDetails(phoneNumber: String): ContactDetails? {
        // Strict validation: Ignore empty, very short, or system placeholders
        if (phoneNumber.isBlank() || phoneNumber.length < 3 || phoneNumber == "Unknown") {
            return null
        }
        
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ContactsContract.PhoneLookup.LOOKUP_KEY
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                    val photoStr = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI))
                    val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                    
                    ContactDetails(
                        name = name,
                        photoUri = photoStr?.let { Uri.parse(it) },
                        lookupKey = lookupKey,
                        phoneNumber = phoneNumber
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Searches for contacts by name or number.
     */
    fun searchContacts(query: String): List<ContactDetails> {
        if (query.isBlank()) return emptyList()
        
        val results = mutableListOf<ContactDetails>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val photoStr = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI))
                    
                    results.add(ContactDetails(
                        name = name,
                        phoneNumber = number,
                        photoUri = photoStr?.let { Uri.parse(it) }
                    ))
                }
            }
        } catch (e: Exception) { }
        
        return results.distinctBy { it.phoneNumber }
    }

    /**
     * Helper to launch Add Contact intent.
     */
    fun getAddContactIntent(phoneNumber: String, name: String? = null): android.content.Intent {
        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        }
        return intent
    }
}
