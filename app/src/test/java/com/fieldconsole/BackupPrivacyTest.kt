package com.fieldconsole

import android.app.Application
import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupPrivacyTest {
    @Test fun `merged application disables automatic backup`() {
        val info = RuntimeEnvironment.getApplication().applicationInfo
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun `all private data domains excluded from legacy cloud and device transfer`() {
        val required = setOf("root", "file", "database", "sharedpref", "external")
        for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            val parser = RuntimeEnvironment.getApplication().resources.getXml(resource)
            val scopes = mutableMapOf<String, MutableSet<String>>()
            var scope = ""
            parser.use {
                while (it.eventType != XmlPullParser.END_DOCUMENT) {
                    if (it.eventType == XmlPullParser.START_TAG) {
                        if (it.name in setOf("full-backup-content", "cloud-backup", "device-transfer")) {
                            scope = it.name
                            scopes[scope] = mutableSetOf()
                        }
                        assertNotEquals("include", it.name)
                        if (it.name == "exclude") {
                            assertEquals(".", it.getAttributeValue(null, "path"))
                            scopes.getValue(scope).add(it.getAttributeValue(null, "domain"))
                        }
                    }
                    it.next()
                }
            }
            assertEquals(if (resource == R.xml.backup_rules) 1 else 2, scopes.size)
            scopes.values.forEach { assertEquals(required, it) }
        }
    }
}
