package com.seudominio.notihook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seudominio.notihook.R
import com.seudominio.notihook.util.Permissions
import com.seudominio.notihook.util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenEvent: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val importMsg = stringResource(R.string.import_needs_service)
    val importDone = stringResource(R.string.import_done)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StatusCard(
                status = status,
                onEnableAccess = { Permissions.openNotificationAccessSettings(context) },
                onBattery = { Permissions.openBatterySettings(context) },
                onImportActive = {
                    vm.importActive { count ->
                        scope.launch {
                            snackbar.showSnackbar(
                                if (count == null) importMsg else importDone.format(count)
                            )
                        }
                    }
                },
            )

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_events)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_messages)) })
            }

            when (tab) {
                0 -> EventsTab(vm, onOpenEvent)
                else -> MessagesTab(vm, onOpenEvent)
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: StatusUi,
    onEnableAccess: () -> Unit,
    onBattery: () -> Unit,
    onImportActive: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),

    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            StatusRow(
                stringResource(R.string.status_access),
                stringResource(
                    if (status.hasAccess) R.string.status_access_on else R.string.status_access_off
                ),
                ok = status.hasAccess,
            )
            StatusRow(
                stringResource(R.string.status_service),
                stringResource(
                    if (status.connected) R.string.status_service_connected
                    else R.string.status_service_disconnected
                ),
                ok = status.connected,
            )
            StatusRow(stringResource(R.string.status_events), status.eventCount.toString())
            StatusRow(stringResource(R.string.status_messages), status.messageCount.toString())
            StatusRow(
                stringResource(R.string.status_last_event),
                status.lastEventAt?.let { formatDateTime(it) } ?: "—",
            )

            if (status.paused) {
                Text(
                    stringResource(R.string.status_paused),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!status.hasAccess) {
                Text(
                    stringResource(R.string.warn_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (!status.connected) {
                Text(
                    stringResource(R.string.warn_service_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!status.hasAccess) {
                    TextButton(onClick = onEnableAccess) {
                        Text(stringResource(R.string.action_enable_access))
                    }
                }
                TextButton(onClick = onBattery) { Text(stringResource(R.string.action_battery)) }
            }
            TextButton(onClick = onImportActive) {
                Text(stringResource(R.string.action_import_active))
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when (ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun MessagesTab(vm: MainViewModel, onOpenEvent: (Long) -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val query by vm.messageQueryState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = query,
            onValueChange = vm::setMessageQuery,
            hint = stringResource(R.string.search_messages),
        )
        if (messages.isEmpty()) {
            EmptyState(stringResource(R.string.empty_messages))
            return@Column
        }
        // Agrupa por conversa preservando a ordem (mais recente primeiro).
        val grouped = remember(messages) { messages.groupBy { it.conversation } }
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (conversation, list) ->
                item(key = "header-$conversation") {
                    Text(
                        conversation,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                items(list, key = { it.id }) { m ->
                    MessageRow(m, onClick = { onOpenEvent(m.eventId) })
                }
            }
        }
    }
}
