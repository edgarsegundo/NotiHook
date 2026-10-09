package com.seudominio.notihook.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Filtros da aba Eventos. */
data class EventFilter(
    val query: String = "",
    val eventTypes: Set<String> = emptySet(),
    val hideOngoing: Boolean = false,
    val hideSummaries: Boolean = false,
    val channelId: String? = null,
    val packageName: String? = null,
    val limit: Int = 1000,
)

class Repository(
    private val dao: EventDao,
    val settings: SettingsStore,
) {

    // Estado em memória do serviço, observado pela UI.
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    // ---- escrita ----

    /** Grava o evento e, em seguida, suas mensagens (que precisam do eventId). */
    suspend fun saveEvent(event: CapturedEvent, messages: List<ParsedMessage>): Long {
        val id = dao.insertEvent(event)
        if (messages.isNotEmpty()) {
            dao.insertMessages(messages.map { it.copy(eventId = id) })
        }
        return id
    }

    suspend fun deleteEvent(id: Long) {
        dao.deleteMessagesOfEvent(id)
        dao.deleteEvent(id)
    }

    suspend fun deleteAll() {
        dao.deleteAllMessages()
        dao.deleteAllEvents()
    }

    /** Aplica a retenção configurada. 0 = infinito. */
    suspend fun applyRetention(): Int {
        val days = settings.retentionDaysNow()
        if (days <= 0) return 0
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val removed = dao.deleteEventsOlderThan(cutoff)
        dao.deleteMessagesOlderThan(cutoff)
        return removed
    }

    // ---- leitura ----

    fun observeEvents(filter: EventFilter): Flow<List<EventListItem>> =
        dao.observeEventsRaw(buildEventQuery(filter))

    fun observeEvent(id: Long) = dao.observeEvent(id)

    fun observeMessages(query: String, limit: Int = 2000): Flow<List<ParsedMessage>> =
        if (query.isBlank()) dao.observeMessages(limit) else dao.searchMessages(query, limit)

    fun observeChannelIds() = dao.observeChannelIds()
    fun observePackages() = dao.observePackages()
    fun observeEventCount() = dao.observeEventCount()
    fun observeMessageCount() = dao.observeMessageCount()
    fun observeLastEventAt() = dao.observeLastEventAt()

    suspend fun eventIdsForKey(sbnKey: String) = dao.eventIdsForKey(sbnKey)
    suspend fun allEventsForExport() = dao.allEventsForExport()
    suspend fun allMessagesForExport() = dao.allMessagesForExport()

    companion object {
        /**
         * Monta o SELECT da lista de eventos com os filtros ativos. Os valores
         * vão sempre como bind args — nunca interpolados no SQL.
         */
        internal fun buildEventQuery(filter: EventFilter): SimpleSQLiteQuery {
            val where = mutableListOf<String>()
            val args = mutableListOf<Any?>()

            val q = filter.query.trim()
            if (q.isNotEmpty()) {
                where += "(title LIKE ? OR text LIKE ? OR conversationTitle LIKE ? OR extrasJson LIKE ?)"
                val like = "%$q%"
                repeat(4) { args += like }
            }
            if (filter.eventTypes.isNotEmpty()) {
                where += "eventType IN (${filter.eventTypes.joinToString(",") { "?" }})"
                args.addAll(filter.eventTypes)
            }
            if (filter.hideOngoing) where += "isOngoing = 0"
            if (filter.hideSummaries) where += "isSummary = 0"
            filter.channelId?.let { where += "channelId = ?"; args += it }
            filter.packageName?.let { where += "packageName = ?"; args += it }

            val sql = buildString {
                append(
                    "SELECT id, capturedAt, eventType, title, text, channelId, " +
                        "packageName, sbnKey, isSummary, isOngoing FROM captured_event"
                )
                if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
                append(" ORDER BY capturedAt DESC LIMIT ?")
            }
            args += filter.limit
            return SimpleSQLiteQuery(sql, args.toTypedArray())
        }
    }
}
