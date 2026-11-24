package com.fieldconsole.ui.history

import com.fieldconsole.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack:  () -> Unit,
    onOpen: (FieldEntry) -> Unit,
    vm:      HistoryViewModel = hiltViewModel()
) {
    val entries by vm.allEntries.collectAsState()
    val count   by vm.entryCount.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title  = { Text(stringResource(R.string.delete_all_title)) },
            text   = { Text(stringResource(R.string.delete_count, count)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.observations), style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.saved_count, count), style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Delete All",
                                 tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HistoryToggleOff, null,
                         modifier = Modifier.size(64.dp),
                         tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.new_observation),
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.first_observation),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    ObservationCard(entry, vm, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(entry: FieldEntry, vm: HistoryViewModel, onOpen: (FieldEntry) -> Unit) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    var showDelete by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title  = { Text(stringResource(R.string.delete_one)) },
            text   = { Text(stringResource(R.string.delete_warning)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(entry); showDelete = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Card(
        onClick = { onOpen(entry) },
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null,
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (entry.placeName.isNotBlank()) entry.placeName
                           else "${entry.latitude}, ${entry.longitude}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick  = { vm.toggleFavorite(entry) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (entry.isFavorite) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick  = { showDelete = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, "Delete",
                         tint = MaterialTheme.colorScheme.error,
                         modifier = Modifier.size(18.dp))
                }
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.note,
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     maxLines = 3)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.tag.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("#${entry.tag}",
                             style = MaterialTheme.typography.labelSmall,
                             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (entry.priority.isNotBlank()) {
                    val c = when (entry.priority.uppercase()) {
                        "HIGH"   -> MaterialTheme.colorScheme.error
                        "MEDIUM" -> MaterialTheme.colorScheme.tertiary
                        else     -> MaterialTheme.colorScheme.secondary
                    }
                    Surface(color = c.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(entry.priority, style = MaterialTheme.typography.labelSmall, color = c,
                             modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(dateFmt.format(Date(entry.timestamp)),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
