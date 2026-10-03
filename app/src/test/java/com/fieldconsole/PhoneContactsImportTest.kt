package com.fieldconsole

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import com.fieldconsole.ui.network.PhoneContactsImport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhoneContactsImportTest {
    @Test fun `permission denial and revocation never query address book`() {
        val app = RuntimeEnvironment.getApplication()
        val provider = SyntheticContactsProvider()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertThrows(IllegalStateException::class.java) { PhoneContactsImport.preview(app) }
        assertEquals(0, provider.queries)
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals(1, PhoneContactsImport.preview(app).entities.size)
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertThrows(IllegalStateException::class.java) { PhoneContactsImport.preview(app) }
        assertEquals(1, provider.queries)
    }
    @Test fun `explicit read-only preview preserves typed values without writing or geocoding`() {
        val app = RuntimeEnvironment.getApplication()
        val provider = SyntheticContactsProvider()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals(0, provider.queries)
        val first = PhoneContactsImport.preview(app)
        val again = PhoneContactsImport.preview(app)
        val entity = first.entities.single()
        assertEquals(entity.get("id"), again.entities.single().get("id"))
        assertEquals("mobile", entity.getAsJsonArray("phones")[0].asJsonObject.get("label").asString)
        assertEquals("Éxample Synthetic", entity.get("display_name").asString)
        assertTrue(entity.getAsJsonArray("associations")[0].asJsonObject.get("point").isJsonNull)
        assertTrue(first.warnings.isNotEmpty())
    }
}

class SyntheticContactsProvider : ContentProvider() {
    var queries = 0
    override fun onCreate() = true
    override fun getType(uri: Uri) = ContactsContract.Data.CONTENT_TYPE
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        queries++
        val cols = projection!!
        val cursor = MatrixCursor(cols)
        listOf(
            mapOf(ContactsContract.Data.MIMETYPE to ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.Data.DATA1 to "+1 202 555 0100", ContactsContract.Data.DATA2 to "2"),
            mapOf(ContactsContract.Data.MIMETYPE to ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                ContactsContract.Data.DATA1 to "1 Example Street, Example City", ContactsContract.Data.DATA2 to "2",
                ContactsContract.Data.DATA4 to "1 Example Street", ContactsContract.Data.DATA7 to "Example City",
                ContactsContract.Data.DATA10 to "Example Country")
        ).forEach { values ->
            cursor.addRow(cols.map { column -> when (column) {
                ContactsContract.Data.CONTACT_ID -> "1"
                ContactsContract.Data.LOOKUP_KEY -> "synthetic-lookup"
                ContactsContract.Data.DISPLAY_NAME -> "Éxample Synthetic"
                else -> values[column].orEmpty()
            } }.toTypedArray())
        }
        return cursor
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only test provider forbids writes")
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("Read-only test provider forbids writes")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("Read-only test provider forbids writes")
}
