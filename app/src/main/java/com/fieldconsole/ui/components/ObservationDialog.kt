package com.fieldconsole.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fieldconsole.location.GeoContext

/**
 * ObservationDialog — shown after GPS fix to capture note and tag.
 * Displays the resolved place name and coordinates from [GeoContext].
 */
@Composable
fun ObservationDialog(
    geo: GeoContext,
    onConfirm: (note: String, tag: String, geo: GeoContext) -> Unit,
    onDismiss: () -> Unit
) {
    var note     by remember { mutableStateOf("") }
    var tag      by remember { mutableStateOf("") }
    val tagFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Location Captured", style = MaterialTheme.typography.titleLarge,
                 fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Place name + coordinates card
                Surface(
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    shape    = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text       = geo.fullPlaceName,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text  = "${geo.coordString}  (\u00b1${geo.accuracy.toInt()} m)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                           .copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value           = note,
                    onValueChange   = { note = it },
                    label           = { Text("Observation (optional)") },
                    placeholder     = { Text("What do you see here?") },
                    maxLines        = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { tagFocus.requestFocus() }),
                    modifier        = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value           = tag,
                    onValueChange   = { tag = it },
                    label           = { Text("Tag (optional)") },
                    placeholder     = { Text("e.g. infrastructure, risk, logistics") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(note, tag, geo) }),
                    modifier        = Modifier
                        .fillMaxWidth()
                        .focusRequester(tagFocus)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note, tag, geo) }) {
                Text("OPEN MATRIX")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
