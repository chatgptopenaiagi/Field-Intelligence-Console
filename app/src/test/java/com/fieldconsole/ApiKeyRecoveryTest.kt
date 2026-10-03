package com.fieldconsole

import android.app.Application
import android.content.Context
import com.fieldconsole.security.ApiKeyStore
import com.fieldconsole.security.ApiProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApiKeyRecoveryTest {
    @Test fun unreadableAndPartialRecordsRemainAvailableForExplicitRecovery() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("fic_secure_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("api_openai", "synthetic-invalid-encoding").putString("api_openai_iv", "bad").commit()
        val store = ApiKeyStore(context)
        assertNull(store.getKey(ApiProvider.OPENAI))
        assertEquals(ApiKeyStore.Status.UNREADABLE, store.status(ApiProvider.OPENAI))
        assertEquals("synthetic-invalid-encoding", prefs.getString("api_openai", null))
        prefs.edit().remove("api_openai").commit()
        assertEquals(ApiKeyStore.Status.UNREADABLE, store.status(ApiProvider.OPENAI))
        assertTrue(prefs.contains("api_openai_iv"))
        store.deleteKey(ApiProvider.OPENAI)
        assertEquals(ApiKeyStore.Status.MISSING, store.status(ApiProvider.OPENAI))
    }
}
