package com.seudominio.notihook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tipos de evento gravados na tabela. */
object EventType {
    const val POSTED = "POSTED"
    const val REMOVED = "REMOVED"
    const val SNAPSHOT = "SNAPSHOT"
}

/**
 * Um registro por evento de notificação, sem dedupe: cada atualização que o
 * WhatsApp faz na notificação de uma conversa gera uma nova linha, o que
 * permite ver como a notificação evolui.
 */
@Entity(
    tableName = "captured_event",
    indices = [
        Index("capturedAt"),
        Index("packageName"),
        Index("sbnKey"),
    ],
)
data class CapturedEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val eventType: String,
    val removalReason: Int? = null,

    // StatusBarNotification
    val sbnKey: String,
    val packageName: String,
    val notifId: Int,
    val tag: String? = null,
    val postTime: Long,
    val groupKey: String? = null,
    val userHandle: String,
    val isOngoing: Boolean,
    val isClearable: Boolean,

    // Notification
    val flags: Int,
    val category: String? = null,
    val channelId: String? = null,
    val group: String? = null,
    val sortKey: String? = null,
    val shortcutId: String? = null,
    val whenTime: Long,

    // Textos
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    val textLines: String? = null,

    // Conversa
    val template: String? = null,
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean? = null,

    // Estruturas serializadas
    val messagesJson: String? = null,
    val historicMessagesJson: String? = null,
    val actionsJson: String? = null,

    val hasLargeIcon: Boolean = false,
    val hasSmallIcon: Boolean = false,

    /** Dump completo de todos os extras. */
    val extrasJson: String,

    val isSummary: Boolean = false,
)
