package com.fieldconsole

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.network.NetworkRepository
import com.fieldconsole.ui.network.NetworkScreen
import com.fieldconsole.ui.theme.FieldIntelligenceTheme
import com.fieldconsole.viewmodel.NetworkViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Real Compose UI with a random isolated database; document-picker navigation is a separate gate. */
@RunWith(AndroidJUnit4::class)
class NetworkUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "network-ui-test-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase
    private lateinit var vm: NetworkViewModel

    @Before fun setup() {
        db = AppDatabase.create(context, name)
        vm = NetworkViewModel(NetworkRepository(db))
        compose.setContent { FieldIntelligenceTheme { NetworkScreen(null, {}, vm) } }
    }
    @After fun cleanup() {
        check(name.startsWith("network-ui-test-") && name != "field_console.db")
        db.close(); context.deleteDatabase(name)
    }
    private fun count() = runBlocking { db.networkDao().entities().size }
    private fun preview() {
        val bytes = instrumentation.context.assets.open("fixtures/contacts.vcf").use { it.readBytes() }
        compose.runOnIdle { vm.readPreview({ bytes }, "vcf") }
        compose.waitUntil(10_000) { vm.preview.value != null && !vm.busy.value }
        compose.onNodeWithText("Review import").assertIsDisplayed()
    }
    private fun confirm() {
        compose.onNodeWithText("Select all valid records").performClick()
        compose.onNodeWithText("Import selected").performClick()
        compose.waitUntil(10_000) { vm.preview.value == null && !vm.busy.value }
    }
    @Test fun previewCancelImportRepeatAndSearchUseActualUiState() {
        preview(); assertEquals(0, count())
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, count())
        compose.onNodeWithText("Import cancelled. Store unchanged.").assertExists()
        preview(); confirm(); assertEquals(2, count())
        preview()
        compose.onNodeWithText("Valid: 2; invalid: 0; already stored: 2").assertExists()
        confirm(); assertEquals(2, count())
        compose.onNodeWithText("Search contacts / organizations").performScrollTo().performTextInput("Éxample")
        compose.onNodeWithText("Alex Éxample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic Organisation", substring = false).assertDoesNotExist()
        compose.onNodeWithText("+1-202-555-0100", substring = true).assertDoesNotExist()
    }
    @Test fun manualCreateAndReopenPreserveLocalRecord() {
        compose.onNodeWithText("Add contact").performClick()
        compose.onNodeWithText("display name").performTextInput("Synthetic UI Contact")
        compose.onNodeWithText("Save locally").performClick()
        compose.waitUntil(10_000) { !vm.busy.value && vm.entities.value.size == 1 }
        assertEquals(1, count())
        val reopened = AppDatabase.create(context, name)
        try { assertEquals("Synthetic UI Contact", runBlocking { reopened.networkDao().entities().single().displayName }) }
        finally { reopened.close() }
    }
    @Test fun geographicScopeShowsUnknownUntilQualifiedPlaceThenSavedAssociation() {
        preview(); confirm()
        compose.onNodeWithText("Scope: ALL").performScrollTo().performClick()
        compose.onNodeWithText("city").performClick()
        compose.onNodeWithText("INSUFFICIENT ADDRESS DATA", substring = true).assertExists()
        compose.onNodeWithText("0 saved associations", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Select place").performScrollTo().performClick()
        compose.onNodeWithText("locality", substring = false).performScrollTo().performTextInput("Example City")
        compose.onNodeWithText("country", substring = false).performScrollTo().performTextInput("Example Country")
        compose.onNodeWithText("Use selected place").performClick()
        compose.onNodeWithText("MATCHES FOUND", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 saved associations (unique entities)", substring = true).assertExists()
        compose.onNodeWithText("Alex Éxample").performScrollTo().assertIsDisplayed()
    }
}
