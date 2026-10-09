package com.seudominio.notihook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seudominio.notihook.R
import com.seudominio.notihook.data.EventListItem
import com.seudominio.notihook.data.EventType
import com.seudominio.notihook.data.ParsedMessage
import com.seudominio.notihook.util.formatTime

@Composable
fun EventsTab(vm: MainViewModel, onOpenEvent: (Long) -> Unit) {
    val events by vm.events.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val channels by vm.channelIds.collectAsStateWithLifecycle()
    val packages by vm.packages.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = filter.query,
            onValueChange = vm::setQuery,
            hint = stringResource(R.string.search_events),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(EventType.POSTED, EventType.REMOVED, EventType.SNAPSHOT).forEach { type ->
                FilterChip(
                    selected = type in filter.eventTypes,
                    onClick = { vm.toggleEventType(type) },
                    label = { Text(type) },
                )
            }
            FilterChip(
                selected = filter.hideOngoing,
                onClick = vm::toggleHideOngoing,
                label = { Text(stringResource(R.string.filter_hide_ongoing)) },
            )
            FilterChip(
                selected = filter.hideSummaries,
                onClick = vm::toggleHideSummaries,
                label = { Text(stringResource(R.string.filter_hide_summaries)) },
            )
            PickerChip(
                label = stringResource(R.string.filter_channel),
                selected = filter.channelId,
                options = channels,
                onSelect = vm::setChannel,
            )
            PickerChip(
                label = stringResource(R.string.filter_package),
                selected = filter.packageName,
                options = packages,
                onSelect = vm::setPackage,
            )
            AssistChip(
                onClick = vm::clearFilters,
                label = { Text(stringResource(R.string.filter_clear)) },
            )
        }

        if (events.isEmpty()) {
            EmptyState(stringResource(R.string.empty_events))
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(events, key = { it.id }) { e ->
                EventRow(e, onClick = { onOpenEvent(e.id) })
                HorizontalDivider()
            }
        }
    }
}

/** Chip que abre um menu para escolher um valor (ou "todos"). */
@Composable
private fun PickerChip(
    label: String,
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: label) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("—") },
                onClick = { onSelect(null); open = false },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

@Composable
private fun EventRow(e: EventListItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatTime(e.capturedAt), style = MaterialTheme.typography.labelMedium)
                TypeBadge(e.eventType)
                if (e.isSummary) {
                    Icon(Icons.Default.Layers, "resumo", Modifier.size(14.dp))
                }
                if (e.isOngoing) {
                    Icon(Icons.Default.Sync, "ongoing", Modifier.size(14.dp))
                }
            }
            Text(
                e.title ?: "(sem título)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            e.text?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            e.channelId?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val color = when (type) {
        EventType.POSTED -> MaterialTheme.colorScheme.primary
        EventType.REMOVED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Text(
        type,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun MessageRow(m: ParsedMessage, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.senderName ?: "(sem remetente)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(formatTime(m.timestamp), style = MaterialTheme.typography.labelSmall)
        }
        SelectionContainer {
            Text(m.text.ifBlank { "(vazio)" }, style = MaterialTheme.typography.bodyMedium)
        }
        m.mimeType?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, null)
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
