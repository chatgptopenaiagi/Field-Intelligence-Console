package com.fieldconsole

import com.fieldconsole.network.NetworkCodec
import com.fieldconsole.ui.network.canSaveContactDraft
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ContactEditorTest {
    @Test fun `blank and label-only drafts cannot be saved`() {
        val draft = NetworkCodec.newEntity("")
        assertFalse(canSaveContactDraft(draft))
        draft.addProperty("notes", "A note does not identify a contact")
        draft.getAsJsonArray("phones").add(JsonObject().apply { addProperty("label", "work"); addProperty("value", "   ") })
        draft.getAsJsonArray("addresses").add(JsonObject().apply { addProperty("id", "synthetic"); addProperty("label", "home"); addProperty("original", "\t") })
        assertFalse(canSaveContactDraft(draft))
    }

    @Test fun `nameless original phone and email enable save without inventing name`() {
        for ((key, value) in listOf("phones" to "+1 202 555 0100", "emails" to "synthetic@example.invalid")) {
            val draft = NetworkCodec.newEntity("")
            draft.getAsJsonArray(key).add(JsonObject().apply { addProperty("value", value) })
            val before = draft.toString()
            assertTrue(canSaveContactDraft(draft))
            assertEquals("", draft.get("display_name").asString)
            assertEquals(before, draft.toString())
        }
    }

    @Test fun `original or incomplete structured address enables nameless save`() {
        for (field in listOf("original", "po_box", "extended", "street", "neighborhood", "locality", "region", "postal_code", "country")) {
            val draft = NetworkCodec.newEntity("")
            draft.getAsJsonArray("addresses").add(JsonObject().apply { addProperty(field, "Synthetic value") })
            assertTrue(field, canSaveContactDraft(draft))
        }
    }

    @Test fun `named and organization records retain existing save behavior`() {
        assertTrue(canSaveContactDraft(NetworkCodec.newEntity("Synthetic person")))
        assertTrue(canSaveContactDraft(NetworkCodec.newEntity("").apply { addProperty("organization", "Synthetic organization") }))
    }
}
