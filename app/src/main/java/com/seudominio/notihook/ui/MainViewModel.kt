package com.seudominio.notihook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seudominio.notihook.NotiHookApp
import com.seudominio.notihook.capture.WaListenerService
import com.seudominio.notihook.data.CapturedEvent
import com.seudominio.notihook.data.EventFilter
import com.seudominio.notihook.data.EventListItem
import com.seudominio.notihook.data.ParsedMessage
import com.seudominio.notihook.data.SettingsStore
import com.seudominio.notihook.util.Export
import com.seudominio.notihook.util.Permissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resumo mostrado no card de status da tela inicial. */
data class StatusUi(
    val hasAccess: Boolean = false,
    val connected: Boolean = false,
    val eventCount: Int = 0,
    val messageCount: Int = 0,
    val lastEventAt: Long? = null,
    val paused: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NotiHookApp).repository

    // ---- status ----

    private val hasAccess = MutableStateFlow(Permissions.hasNotificationAccess(app))

    /** Chamado em ON_RESUME: a permissão pode ter mudado fora do app. */
    fun refreshAccess() {
        hasAccess.value = Permissions.hasNotificationAccess(getApplication())
    }

    val status: StateFlow<StatusUi> = combine(
        hasAccess,
        repo.listenerConnected,
        repo.observeEventCount(),
        repo.observeMessageCount(),
        repo.observeLastEventAt(),
        repo.settings.capturePaused,
    ) { values ->
        StatusUi(
            hasAccess = values[0] as Boolean,
            connected = values[1] as Boolean,
            eventCount = values[2] as Int,
            messageCount = values[3] as Int,
            lastEventAt = values[4] as Long?,
            paused = values[5] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUi())

    // ---- eventos ----

    private val _filter = MutableStateFlow(EventFilter())
    val filter: StateFlow<EventFilter> = _filter.asStateFlow()

    val events: StateFlow<List<EventListItem>> = _filter
        .debounce(150)
        .flatMapLatest { repo.observeEvents(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channelIds: StateFlow<List<String>> = repo.observeChannelIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val packages: StateFlow<List<String>> = repo.observePackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun toggleEventType(type: String) = _filter.update {
        it.copy(eventTypes = if (type in it.eventTypes) it.eventTypes - type else it.eventTypes + type)
    }
    fun toggleHideOngoing() = _filter.update { it.copy(hideOngoing = !it.hideOngoing) }
    fun toggleHideSummaries() = _filter.update { it.copy(hideSummaries = !it.hideSummaries) }
    fun setChannel(id: String?) = _filter.update { it.copy(channelId = id) }
    fun setPackage(pkg: String?) = _filter.update { it.copy(packageName = pkg) }
    fun clearFilters() = _filter.update { EventFilter(query = it.query) }

    private fun MutableStateFlow<EventFilter>.update(block: (EventFilter) -> EventFilter) {
        value = block(value)
    }

    // ---- mensagens ----

    private val messageQuery = MutableStateFlow("")
    fun setMessageQuery(q: String) { messageQuery.value = q }
    val messageQueryState: StateFlow<String> = messageQuery.asStateFlow()

    val messages: StateFlow<List<ParsedMessage>> = messageQuery
        .debounce(150)
        .flatMapLatest { repo.observeMessages(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- detalhe ----

    fun observeEvent(id: Long) = repo.observeEvent(id)

    private val _siblings = MutableStateFlow<List<Long>>(emptyList())
    val siblings: StateFlow<List<Long>> = _siblings.asStateFlow()

    fun loadSiblings(sbnKey: String) {
        viewModelScope.launch { _siblings.value = repo.eventIdsForKey(sbnKey) }
    }

    fun deleteEvent(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteEvent(id)
            onDone()
        }
    }

    // ---- configurações ----

    val monitoredPackages: StateFlow<Set<String>> = repo.settings.monitoredPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf(SettingsStore.DEFAULT_PACKAGE))

    val retentionDays: StateFlow<Int> = repo.settings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_RETENTION_DAYS)

    val paused: StateFlow<Boolean> = repo.settings.capturePaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun addPackage(pkg: String) = viewModelScope.launch { repo.settings.addPackage(pkg) }
    fun removePackage(pkg: String) = viewModelScope.launch { repo.settings.removePackage(pkg) }
    fun setRetentionDays(days: Int) = viewModelScope.launch { repo.settings.setRetentionDays(days) }
    fun setPaused(p: Boolean) = viewModelScope.launch { repo.settings.setPaused(p) }

    fun deleteAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteAll()
            onDone()
        }
    }

    /** Monta o JSON de exportação fora da thread principal. */
    fun buildExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = Export.buildJson(repo.allEventsForExport(), repo.allMessagesForExport())
            onReady(json)
        }
    }

    /** Importa as ativas; devolve null se o serviço não estiver conectado. */
    fun importActive(onResult: (Int?) -> Unit) {
        val service = WaListenerService.instance
        if (service == null || !repo.listenerConnected.value) {
            onResult(null)
            return
        }
        viewModelScope.launch { onResult(service.importActive()) }
    }

    /** Um único JSON para copiar/compartilhar no detalhe. */
    fun eventJson(event: CapturedEvent): String = Export.eventToJson(event).toString(2)

    companion object {
        val Factory = object : ViewModelProvider.AndroidViewModelFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = extras[APPLICATION_KEY] as Application
                return MainViewModel(app) as T
            }
        }
    }
}
