package com.seudominio.notihook.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seudominio.notihook.R
import com.seudominio.notihook.util.Export
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val packages by vm.monitoredPackages.collectAsStateWithLifecycle()
    val retention by vm.retentionDays.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var newPackage by remember { mutableStateOf("") }
    var retentionText by remember(retention) { mutableStateOf(retention.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Guarda o JSON até o usuário escolher o destino no seletor de arquivos.
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val exportDone = stringResource(R.string.export_done)
    val exportFailed = stringResource(R.string.export_failed)
    val deletedAll = stringResource(R.string.deleted_all)

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            true
        } catch (t: Throwable) {
            false
        }
        scope.launch { snackbar.showSnackbar(if (ok) exportDone else exportFailed) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Pacotes monitorados
            Text(
                stringResource(R.string.settings_packages),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            packages.sorted().forEach { pkg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.removePackage(pkg) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.settings_remove))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPackage,
                    onValueChange = { newPackage = it },
                    placeholder = { Text(stringResource(R.string.settings_package_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        vm.addPackage(newPackage)
                        newPackage = ""
                    },
                    enabled = newPackage.isNotBlank(),
                ) { Text(stringResource(R.string.settings_add)) }
            }

            HorizontalDivider()

            // Retenção
            Text(
                stringResource(R.string.settings_retention),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = retentionText,
                    onValueChange = { retentionText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.settings_retention_days)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.setRetentionDays(retentionText.toIntOrNull() ?: 0) },
                ) { Text(stringResource(R.string.save)) }
            }
            Text(
                stringResource(R.string.settings_retention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Pausa
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_pause),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_pause_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = paused, onCheckedChange = vm::setPaused)
            }

            HorizontalDivider()

            // Exportar / apagar
            Button(
                onClick = {
                    vm.buildExport { json ->
                        pendingExport = json
                        createDoc.launch(Export.suggestedFileName())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_export)) }

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_delete_all)) }

            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll { scope.launch { snackbar.showSnackbar(deletedAll) } }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
