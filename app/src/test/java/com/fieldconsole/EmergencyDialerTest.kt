package com.fieldconsole

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import com.fieldconsole.data.emergency.EmergencyNumbers
import com.fieldconsole.intent.EmergencyDialer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EmergencyDialerTest {
    @Test fun `merged application does not request direct calling permission`() {
        val app = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.CALL_PHONE))
    }

    @Test fun `emergency action only opens dialer with encoded number`() {
        var launched: Intent? = null
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        EmergencyDialer.open(context, "+43-1-406-43-43")
        assertEquals(Intent.ACTION_DIAL, launched!!.action)
        assertEquals("tel", launched!!.data!!.scheme)
        assertEquals("+43-1-406-43-43", launched!!.data!!.schemeSpecificPart)
    }

    @Test fun `missing dialer shows number instead of crashing or attempting a call`() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        EmergencyDialer.open(context, "112")
        assertTrue(ShadowToast.getTextOfLatestToast().contains("112"))
    }

    @Test fun `offline country and unknown-country emergency catalogs remain available`() {
        for (country in listOf("AT", "DE", "GB", "US", "CH", "FR", "IT", "RO")) {
            val numbers = EmergencyNumbers.forCountry(country)
            assertTrue(numbers.isNotEmpty())
            assertTrue(numbers.all { it.number.isNotBlank() && it.country == country && it.status == "verified" })
        }
        assertTrue(EmergencyNumbers.forCountry("ZZ").isEmpty())
        assertTrue(EmergencyNumbers.forCountry("").isEmpty())
    }
}
