package com.fieldconsole.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.security.ApiProvider
import com.fieldconsole.viewmodel.EngineSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettingsScreen(
    viewModel: EngineSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        uiState.saveSuccess?.let {
            snackbarHostState.showSnackbar("${it.displayName} key saved.")
            viewModel.clearTransientState()
        }
    }
    LaunchedEffect(uiState.deleteSuccess) {
        uiState.deleteSuccess?.let {
            snackbarHostState.showSnackbar("${it.displayName} key removed.")
            viewModel.clearTransientState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engines & APIs", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info banner
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                shape    = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "API keys are encrypted with AES-256-GCM using the Android " +
                                "Keystore. All features remain available without keys via " +
                                "intent-based fallback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ApiProvider.entries.forEach { provider ->
                ApiKeyCard(
                    provider     = provider,
                    isConfigured = uiState.configuredProviders.contains(provider),
                    onSave       = { key -> viewModel.saveKey(provider, key) },
                    onDelete     = { viewModel.deleteKey(provider) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ApiKeyCard(
    provider: ApiProvider,
    isConfigured: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey  by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = provider.displayName,
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Badge(
                    containerColor = if (isConfigured)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (isConfigured)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(if (isConfigured) "Configured" else "Not set")
                }
            }

            Text(
                text  = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value         = keyInput,
                onValueChange = { keyInput = it },
                label         = { Text("API Key") },
                placeholder   = { Text(if (isConfigured) "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022  (replace)" else "Enter key\u2026") },
                visualTransformation = if (showKey) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = {
                        if (keyInput.isNotBlank()) {
                            onSave(keyInput)
                            keyInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled  = keyInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null,
                         modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Key")
                }

                if (isConfigured) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete key",
                             modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
