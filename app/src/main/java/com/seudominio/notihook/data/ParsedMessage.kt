package com.seudominio.notihook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mensagens individuais extraídas de EXTRA_MESSAGES / EXTRA_HISTORIC_MESSAGES,
 * deduplicadas por [dedupeKey] para que reenvios da mesma notificação não
 * gerem linhas repetidas.
 */
@Entity(
    tableName = "parsed_message",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index("eventId"),
        Index("sbnKey"),
        Index("conversation"),
        Index("timestamp"),
    ],
)
data class ParsedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** FK lógica para captured_event.id (sem constraint, para não apagar em cascata). */
    val eventId: Long,
    val sbnKey: String,
    val conversation: String,
    val isGroup: Boolean,
    val senderName: String? = null,
    val senderKey: String? = null,
    val senderUri: String? = null,
    val text: String,
    val timestamp: Long,
    val mimeType: String? = null,
    val dataUri: String? = null,
    /** "$sbnKey|$timestamp|$text" */
    val dedupeKey: String,
)
