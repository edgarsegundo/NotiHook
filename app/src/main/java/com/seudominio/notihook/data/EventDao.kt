package com.seudominio.notihook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Projeção leve para a lista de eventos (evita carregar extrasJson inteiro). */
data class EventListItem(
    val id: Long,
    val capturedAt: Long,
    val eventType: String,
    val title: String?,
    val text: String?,
    val channelId: String?,
    val packageName: String,
    val sbnKey: String,
    val isSummary: Boolean,
    val isOngoing: Boolean,
)

data class ConversationCount(val conversation: String, val total: Int)

@Dao
interface EventDao {

    // ---- escrita ----

    @Insert
    suspend fun insertEvent(event: CapturedEvent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<ParsedMessage>)

    @Query("DELETE FROM captured_event WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("DELETE FROM parsed_message WHERE eventId = :eventId")
    suspend fun deleteMessagesOfEvent(eventId: Long)

    @Query("DELETE FROM captured_event")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM parsed_message")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM captured_event WHERE capturedAt < :cutoff")
    suspend fun deleteEventsOlderThan(cutoff: Long): Int

    @Query("DELETE FROM parsed_message WHERE timestamp < :cutoff")
    suspend fun deleteMessagesOlderThan(cutoff: Long): Int

    // ---- leitura: eventos ----

    @RawQuery(observedEntities = [CapturedEvent::class])
    fun observeEventsRaw(query: SupportSQLiteQuery): Flow<List<EventListItem>>

    @Query("SELECT * FROM captured_event WHERE id = :id")
    fun observeEvent(id: Long): Flow<CapturedEvent?>

    @Query("SELECT * FROM captured_event ORDER BY capturedAt ASC")
    suspend fun allEventsForExport(): List<CapturedEvent>

    /** Eventos da mesma sbnKey, para navegar anterior/próximo no detalhe. */
    @Query("SELECT id FROM captured_event WHERE sbnKey = :sbnKey ORDER BY capturedAt ASC")
    suspend fun eventIdsForKey(sbnKey: String): List<Long>

    @Query("SELECT DISTINCT channelId FROM captured_event WHERE channelId IS NOT NULL ORDER BY channelId")
    fun observeChannelIds(): Flow<List<String>>

    @Query("SELECT DISTINCT packageName FROM captured_event ORDER BY packageName")
    fun observePackages(): Flow<List<String>>

    // ---- leitura: mensagens ----

    @Query("SELECT * FROM parsed_message ORDER BY timestamp DESC LIMIT :limit")
    fun observeMessages(limit: Int): Flow<List<ParsedMessage>>

    @Query(
        """
        SELECT * FROM parsed_message
        WHERE text LIKE '%' || :q || '%' OR conversation LIKE '%' || :q || '%'
           OR senderName LIKE '%' || :q || '%'
        ORDER BY timestamp DESC LIMIT :limit
        """
    )
    fun searchMessages(q: String, limit: Int): Flow<List<ParsedMessage>>

    @Query("SELECT * FROM parsed_message ORDER BY timestamp ASC")
    suspend fun allMessagesForExport(): List<ParsedMessage>

    // ---- estatísticas ----

    @Query("SELECT COUNT(*) FROM captured_event")
    fun observeEventCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM parsed_message")
    fun observeMessageCount(): Flow<Int>

    @Query("SELECT MAX(capturedAt) FROM captured_event")
    fun observeLastEventAt(): Flow<Long?>
}
