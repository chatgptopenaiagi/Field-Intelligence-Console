package com.fieldconsole.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens the dialer only. The user must explicitly place the call in their phone app. */
object EmergencyDialer {
    fun intent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

    fun open(context: Context, number: String) {
        try {
            context.startActivity(intent(number))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No phone app available. Dial $number using a phone.", Toast.LENGTH_LONG).show()
        }
    }
}
