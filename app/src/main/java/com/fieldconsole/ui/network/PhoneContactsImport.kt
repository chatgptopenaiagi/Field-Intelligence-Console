package com.fieldconsole.ui.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.fieldconsole.network.NetworkCodec
import com.fieldconsole.network.NetworkPreview
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/** Called only from the explicit permission-gated preview action. Never writes Contacts. */
internal object PhoneContactsImport {
    fun preview(context: Context): NetworkPreview {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            "Phone-contact access is unavailable. File import remains available."
        }
        val entities = linkedMapOf<String, JsonObject>()
        val preferences = context.getSharedPreferences("fic_local_import", Context.MODE_PRIVATE)
        val sourceId = preferences.getString("contacts_source", null) ?: "android-contacts:${java.util.UUID.randomUUID()}".also {
            preferences.edit().putString("contacts_source", it).apply()
        }
        val mimeTypes = listOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4, ContactsContract.Data.DATA5, ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7, ContactsContract.Data.DATA8, ContactsContract.Data.DATA9, ContactsContract.Data.DATA10)
        val now = Instant.now().toString()
        var bytes = 0
        var rows = 0
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection,
            "${ContactsContract.Data.MIMETYPE} IN (${mimeTypes.joinToString(",") { "?" }})", mimeTypes.toTypedArray(), ContactsContract.Data.CONTACT_ID)?.use { cursor ->
            fun value(column: String): String = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty().also {
                require(it.toByteArray().size <= 16_384); bytes += it.toByteArray().size; require(bytes <= NetworkCodec.MAX_BYTES)
            }
            while (cursor.moveToNext()) {
                require(++rows <= 50_000)
                val record = value(ContactsContract.Data.LOOKUP_KEY).ifBlank { value(ContactsContract.Data.CONTACT_ID) }
                val entity = entities.getOrPut(record) {
                    require(entities.size < 5000)
                    NetworkCodec.newEntity(value(ContactsContract.Data.DISPLAY_NAME)).apply {
                        addProperty("id", digest("$sourceId:$record"))
                        array("ancestry").add(JsonObject().apply {
                            addProperty("source_id", sourceId); addProperty("record_id", record)
                            addProperty("format", "android-contacts"); addProperty("imported_at", now)
                            add("updated_at", com.google.gson.JsonNull.INSTANCE)
                        })
                    }
                }
                val type = value(ContactsContract.Data.MIMETYPE)
                val original = value(ContactsContract.Data.DATA1)
                val typeNumber = value(ContactsContract.Data.DATA2).toIntOrNull()
                val label = if (type == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) when (typeNumber) {
                    1 -> "home"; 2 -> "mobile"; 3 -> "work"; else -> value(ContactsContract.Data.DATA3).ifBlank { "unspecified" }
                } else when (typeNumber) { 1 -> "home"; 2 -> "work"; else -> value(ContactsContract.Data.DATA3).ifBlank { "unspecified" } }
                when (type) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> entity.array("phones").add(typed(original, NetworkCodec.normalizePhone(original), label))
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> entity.array("emails").add(typed(original, original.trim().lowercase(Locale.ROOT), label))
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> entity.addProperty("notes", original)
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        entity.addProperty("organization", original); entity.addProperty("role", value(ContactsContract.Data.DATA4))
                    }
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> entity.add("structured_name", JsonObject().apply {
                        addProperty("given", value(ContactsContract.Data.DATA2)); addProperty("family", value(ContactsContract.Data.DATA3))
                        addProperty("prefix", value(ContactsContract.Data.DATA4)); addProperty("additional", value(ContactsContract.Data.DATA5)); addProperty("suffix", value(ContactsContract.Data.DATA6))
                    })
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val id = digest("$record:address:${entity.array("addresses").size()}:$original")
                        entity.array("addresses").add(JsonObject().apply {
                            addProperty("id", id); addProperty("original", original); addProperty("label", label)
                            addProperty("street", value(ContactsContract.Data.DATA4)); addProperty("po_box", value(ContactsContract.Data.DATA5))
                            addProperty("extended", ""); addProperty("neighborhood", value(ContactsContract.Data.DATA6))
                            addProperty("locality", value(ContactsContract.Data.DATA7)); addProperty("region", value(ContactsContract.Data.DATA8))
                            addProperty("postal_code", value(ContactsContract.Data.DATA9)); addProperty("country", value(ContactsContract.Data.DATA10))
                        })
                        entity.array("associations").add(savedAssociation(id).apply { addProperty("source", "imported_contact") })
                    }
                }
            }
        } ?: throw IllegalStateException("Phone-contact provider unavailable.")
        NetworkCodec.validateNetwork(NetworkCodec.dataset(entities.values.toList()))
        return NetworkPreview(entities.values.toList(), emptyList(), sourceId, warnings = listOf("Read-only phone snapshot. Photos, linked accounts and unsupported phone metadata are not retained."))
    }
    private fun typed(value: String, normalized: String, label: String) = JsonObject().apply {
        addProperty("value", value); addProperty("normalized", normalized); addProperty("label", label)
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
