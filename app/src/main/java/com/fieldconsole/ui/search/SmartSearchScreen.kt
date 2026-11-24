package com.fieldconsole.ui.search

import com.fieldconsole.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
fun SmartSearchScreen(
    onBack:        () -> Unit,
    onEntrySelect: (FieldEntry) -> Unit,
    vm:            HistoryViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val entries by vm.searchResults(query).collectAsState(initial = emptyList())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = { query = it },
                        placeholder   = { Text(stringResource(R.string.search_hint)) },
                        singleLine    = true,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (entries.isEmpty() && query.isNotBlank()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null,
                         modifier = Modifier.size(48.dp),
                         tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("No results for \"$query\"",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (query.isBlank()) {
                    item {
                        Text(stringResource(R.string.recent),
                             style = MaterialTheme.typography.titleSmall,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    item {
                        Text("${entries.size} result${if (entries.size == 1) "" else "s"} for \"$query\"",
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    SearchResultCard(entry, onClick = { onEntrySelect(entry) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(entry: FieldEntry, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.LocationOn, null,
                 tint = MaterialTheme.colorScheme.primary,
                 modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (entry.placeName.isNotBlank()) {
                    Text(entry.placeName,
                         style = MaterialTheme.typography.bodyMedium,
                         fontWeight = FontWeight.Bold)
                } else {
                    Text("${entry.latitude}, ${entry.longitude}",
                         style = MaterialTheme.typography.bodyMedium,
                         fontWeight = FontWeight.Bold)
                }
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(entry.note,
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         maxLines = 2)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        val priorityColor = when (entry.priority.uppercase()) {
                            "HIGH"     -> MaterialTheme.colorScheme.error
                            "MEDIUM"   -> MaterialTheme.colorScheme.tertiary
                            else       -> MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            color = priorityColor.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(entry.priority,
                                 style = MaterialTheme.typography.labelSmall,
                                 color = priorityColor,
                                 modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(dateFmt.format(Date(entry.timestamp)),
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entry.isFavorite) {
                Icon(Icons.Default.Star, null,
                     tint = MaterialTheme.colorScheme.tertiary,
                     modifier = Modifier.size(16.dp))
            }
        }
    }
}
