package com.seudominio.notihook.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seudominio.notihook.R
import com.seudominio.notihook.capture.BundleDump
import com.seudominio.notihook.data.CapturedEvent
import com.seudominio.notihook.util.formatDateTime
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    vm: MainViewModel,
    eventId: Long,
    onBack: () -> Unit,
    onNavigateToEvent: (Long) -> Unit,
) {
    val context = LocalContext.current
    val event by vm.observeEvent(eventId).collectAsStateWithLifecycle(initialValue = null)
    val siblings by vm.siblings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMsg = stringResource(R.string.copied)

    val current = event

    androidx.compose.runtime.LaunchedEffect(current?.sbnKey) {
        current?.sbnKey?.let { vm.loadSiblings(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    current?.let { e ->
                        IconButton(onClick = {
                            copyToClipboard(context, vm.eventJson(e))
                            scope.launch { snackbar.showSnackbar(copiedMsg) }
                        }) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.action_copy_json))
                        }
                        IconButton(onClick = { shareText(context, vm.eventJson(e)) }) {
                            Icon(Icons.Default.Share, stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { vm.deleteEvent(e.id, onBack) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete_event))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (current == null) {
            EmptyState("—")
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SiblingNav(siblings, current.id, onNavigateToEvent)

            Section(stringResource(R.string.section_system), initiallyExpanded = true) {
                Field("key", current.sbnKey)
                Field("packageName", current.packageName)
                Field("id", current.notifId.toString())
                Field("tag", current.tag)
                Field("postTime", "${formatDateTime(current.postTime)} (${current.postTime})")
                Field("capturedAt", "${formatDateTime(current.capturedAt)} (${current.capturedAt})")
                Field("eventType", current.eventType)
                Field("removalReason", current.removalReason?.toString())
                Field("groupKey", current.groupKey)
                Field("user", current.userHandle)
                Field("isOngoing", current.isOngoing.toString())
                Field("isClearable", current.isClearable.toString())
            }

            Section(stringResource(R.string.section_notification)) {
                val decoded = BundleDump.decodeFlags(current.flags)
                Field("flags", "${current.flags} ${if (decoded.isEmpty()) "" else decoded.joinToString(", ", "[", "]")}")
                Field("category", current.category)
                Field("channelId", current.channelId)
                Field("group", current.group)
                Field("sortKey", current.sortKey)
                Field("shortcutId", current.shortcutId)
                Field("when", "${formatDateTime(current.whenTime)} (${current.whenTime})")
                Field("hasLargeIcon", current.hasLargeIcon.toString())
                Field("hasSmallIcon", current.hasSmallIcon.toString())
                Field("isSummary", current.isSummary.toString())
            }

            Section(stringResource(R.string.section_texts), initiallyExpanded = true) {
                Field("title", current.title)
                Field("text", current.text)
                Field("bigText", current.bigText)
                Field("subText", current.subText)
                Field("summaryText", current.summaryText)
                Field("infoText", current.infoText)
                Field("textLines", current.textLines)
            }

            Section(stringResource(R.string.section_conversation)) {
                Field("template", current.template)
                Field("conversationTitle", current.conversationTitle)
                Field("isGroupConversation", current.isGroupConversation?.toString())
            }

            Section(stringResource(R.string.section_messages), initiallyExpanded = true) {
                MessagesBlock(current.messagesJson)
                current.historicMessagesJson?.let {
                    Text(
                        stringResource(R.string.historic_messages),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    MessagesBlock(it)
                }
            }

            Section(stringResource(R.string.section_actions)) {
                ActionsBlock(current.actionsJson)
            }

            Section(stringResource(R.string.section_extras), initiallyExpanded = true) {
                ExtrasTree(current.extrasJson)
            }

            Section(stringResource(R.string.section_raw)) {
                RawJson(vm.eventJson(current))
            }
        }
    }
}

/** Botões para comparar como a notificação da mesma sbnKey evoluiu. */
@Composable
private fun SiblingNav(siblings: List<Long>, currentId: Long, onNavigate: (Long) -> Unit) {
    if (siblings.size <= 1) return
    val index = siblings.indexOf(currentId)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onNavigate(siblings[index - 1]) },
            enabled = index > 0,
        ) { Text(stringResource(R.string.action_prev)) }

        Text(
            if (index >= 0) "${index + 1}/${siblings.size}" else "—",
            style = MaterialTheme.typography.labelMedium,
        )

        TextButton(
            onClick = { onNavigate(siblings[index + 1]) },
            enabled = index >= 0 && index < siblings.size - 1,
        ) { Text(stringResource(R.string.action_next)) }
    }
}

@Composable
private fun Section(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
        HorizontalDivider()
    }
}

/** Linha rótulo → valor, com o valor selecionável. */
@Composable
private fun Field(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (value != null && value.length > 60) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun MessagesBlock(json: String?) {
    val array = remember(json) { runCatching { JSONArray(json ?: "[]") }.getOrNull() }
    if (array == null || array.length() == 0) {
        Text("—", style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (i in 0 until array.length()) {
        val m = array.optJSONObject(i) ?: continue
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(8.dp)) {
                m.keys().forEach { key ->
                    Field(key, m.opt(key)?.toString())
                }
            }
        }
    }
}

@Composable
private fun ActionsBlock(json: String?) {
    val array = remember(json) { runCatching { JSONArray(json ?: "[]") }.getOrNull() }
    if (array == null || array.length() == 0) {
        Text("—", style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (i in 0 until array.length()) {
        val a = array.optJSONObject(i) ?: continue
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                a.optString("title", "(sem título)"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "RemoteInput: ${a.optBoolean("hasRemoteInput")} · keys=${a.optJSONArray("remoteInputKeys")}" +
                    " · semanticAction=${a.optInt("semanticAction")} · isContextual=${a.optBoolean("isContextual")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Árvore chave → tipo → valor, com recuo para bundles aninhados. */
@Composable
private fun ExtrasTree(extrasJson: String) {
    val root = remember(extrasJson) { runCatching { JSONObject(extrasJson) }.getOrNull() }
    if (root == null) {
        Text(extrasJson, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        return
    }
    Column { JsonNode(root, depth = 0) }
}

@Composable
private fun JsonNode(obj: JSONObject, depth: Int) {
    val keys = obj.keys().asSequence().sorted().toList()
    for (key in keys) {
        val node = obj.optJSONObject(key)
        val type = node?.optString("type")
        val value = node?.opt("value")

        Column(Modifier.fillMaxWidth().padding(start = (depth * 12).dp, top = 4.dp)) {
            Row {
                SelectionContainer {
                    Text(
                        key,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (type != null) {
                    Text(
                        "  ·  ${type.substringAfterLast('.')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (node?.optBoolean("truncated") == true) {
                    Text(
                        "  (truncado)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (value) {
                is JSONObject -> JsonNode(value, depth + 1)
                else -> SelectionContainer {
                    Text(
                        value?.toString() ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun RawJson(json: String) {
    SelectionContainer {
        Text(
            json,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("NotiHook", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (t: Throwable) {
        // sem app para compartilhar
    }
}
