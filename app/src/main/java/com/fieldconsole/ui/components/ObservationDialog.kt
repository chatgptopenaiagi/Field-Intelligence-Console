package com.fieldconsole.ui.components

import com.fieldconsole.R
import androidx.compose.ui.res.stringResource
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
    var note     by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tag      by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val tagFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (geo.manual) R.string.manual_point else R.string.captured), style = MaterialTheme.typography.titleLarge,
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
                                text       = listOfNotNull(geo.fullPlaceName, geo.geocodingNotice?.let { stringResource(R.string.address_unknown) }).joinToString("\n"),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text  = "${geo.coordString}  (${geo.accuracyText})",
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
                    label           = { Text(stringResource(R.string.observation)) },
                    placeholder     = { Text(stringResource(R.string.observation_hint)) },
                    maxLines        = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { tagFocus.requestFocus() }),
                    modifier        = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value           = tag,
                    onValueChange   = { tag = it },
                    label           = { Text(stringResource(R.string.tag)) },
                    placeholder     = { Text(stringResource(R.string.tag_hint)) },
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
                Text(stringResource(R.string.open_matrix))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
