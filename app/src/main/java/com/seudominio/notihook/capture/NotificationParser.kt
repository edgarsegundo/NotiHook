package com.seudominio.notihook.capture

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import com.seudominio.notihook.data.CapturedEvent
import com.seudominio.notihook.data.EventType
import com.seudominio.notihook.data.ParsedMessage
import org.json.JSONArray
import org.json.JSONObject

/** Resultado do parsing: o evento e as mensagens individuais encontradas. */
data class ParseResult(
    val event: CapturedEvent,
    val messages: List<ParsedMessage>,
)

/**
 * Converte uma [StatusBarNotification] no par evento + mensagens.
 *
 * Toda a extração é defensiva: um extra inesperado nunca pode derrubar o
 * serviço, então falhas individuais viram texto no JSON em vez de exceção.
 */
object NotificationParser {

    fun parse(
        sbn: StatusBarNotification,
        eventType: String = EventType.POSTED,
        removalReason: Int? = null,
        capturedAt: Long = System.currentTimeMillis(),
    ): ParseResult {
        val n = sbn.notification
        val extras: Bundle = n.extras ?: Bundle()

        val messagesJson = messagesArray(extras, Notification.EXTRA_MESSAGES)
        val historicJson = messagesArray(extras, Notification.EXTRA_HISTORIC_MESSAGES)

        val conversationTitle = str(extras, Notification.EXTRA_CONVERSATION_TITLE)
        val title = str(extras, Notification.EXTRA_TITLE)
        val text = str(extras, Notification.EXTRA_TEXT)
        val isGroup = bool(extras, Notification.EXTRA_IS_GROUP_CONVERSATION)

        // Fallback: sem EXTRA_MESSAGES, registra title+text como uma mensagem.
        val effectiveMessages = if (messagesJson.length() > 0) {
            messagesJson
        } else {
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("text", text ?: "")
                        put("timestamp", if (n.`when` != 0L) n.`when` else sbn.postTime)
                        put("senderName", title ?: JSONObject.NULL)
                        put("senderKey", JSONObject.NULL)
                        put("senderUri", JSONObject.NULL)
                        put("dataMimeType", JSONObject.NULL)
                        put("dataUri", JSONObject.NULL)
                        put("fallback", true)
                    }
                )
            }
        }

        val event = CapturedEvent(
            capturedAt = capturedAt,
            eventType = eventType,
            removalReason = removalReason,
            sbnKey = safe { sbn.key } ?: "",
            packageName = safe { sbn.packageName } ?: "",
            notifId = safe { sbn.id } ?: 0,
            tag = safe { sbn.tag },
            postTime = safe { sbn.postTime } ?: 0L,
            groupKey = safe { sbn.groupKey },
            userHandle = safe { sbn.user?.toString() } ?: "",
            isOngoing = safe { sbn.isOngoing } ?: false,
            isClearable = safe { sbn.isClearable } ?: false,
            flags = n.flags,
            category = n.category,
            channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safe { n.channelId } else null,
            group = safe { n.group },
            sortKey = safe { n.sortKey },
            shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) safe { n.shortcutId } else null,
            whenTime = n.`when`,
            title = title,
            text = text,
            bigText = str(extras, Notification.EXTRA_BIG_TEXT),
            subText = str(extras, Notification.EXTRA_SUB_TEXT),
            summaryText = str(extras, Notification.EXTRA_SUMMARY_TEXT),
            infoText = str(extras, Notification.EXTRA_INFO_TEXT),
            textLines = textLines(extras),
            template = str(extras, Notification.EXTRA_TEMPLATE),
            conversationTitle = conversationTitle,
            isGroupConversation = isGroup,
            messagesJson = effectiveMessages.toString(),
            historicMessagesJson = historicJson.takeIf { it.length() > 0 }?.toString(),
            actionsJson = actionsJson(n).toString(),
            hasLargeIcon = hasLargeIcon(n, extras),
            hasSmallIcon = safe { n.smallIcon != null } ?: false,
            extrasJson = BundleDump.bundleToJson(extras).toString(),
            isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )

        val conversation = conversationTitle ?: title ?: event.sbnKey
        val messages = buildMessages(event, effectiveMessages, conversation, isGroup ?: false) +
            buildMessages(event, historicJson, conversation, isGroup ?: false)

        return ParseResult(event, messages.distinctBy { it.dedupeKey })
    }

    /** Evento mínimo para REMOVED, onde só os metadados básicos interessam. */
    fun parseRemoved(sbn: StatusBarNotification, reason: Int?): ParseResult =
        parse(sbn, EventType.REMOVED, reason)

    /** Evento de erro, para nunca perder o registro de uma notificação. */
    fun errorEvent(
        sbn: StatusBarNotification?,
        eventType: String,
        error: Throwable,
    ): CapturedEvent = CapturedEvent(
        capturedAt = System.currentTimeMillis(),
        eventType = eventType,
        sbnKey = safe { sbn?.key } ?: "",
        packageName = safe { sbn?.packageName } ?: "",
        notifId = safe { sbn?.id } ?: 0,
        postTime = safe { sbn?.postTime } ?: 0L,
        userHandle = safe { sbn?.user?.toString() } ?: "",
        isOngoing = false,
        isClearable = false,
        flags = 0,
        whenTime = 0L,
        extrasJson = JSONObject().put("parse_error", error.toString()).toString(),
    )

    // ---- mensagens ----

    /**
     * Serializa EXTRA_MESSAGES / EXTRA_HISTORIC_MESSAGES usando a API oficial
     * e, se ela falhar, caindo para a leitura manual dos Bundles.
     */
    private fun messagesArray(extras: Bundle, key: String): JSONArray {
        val arr = JSONArray()
        val raw: Array<Parcelable>? = safe {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(key)
        }
        if (raw == null || raw.isEmpty()) return arr

        val parsed = safe { Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw) }
        if (parsed != null) {
            for (m in parsed) {
                arr.put(
                    JSONObject().apply {
                        put("text", m.text?.toString() ?: "")
                        put("timestamp", m.timestamp)
                        put("senderName", m.senderPerson?.name?.toString() ?: m.sender?.toString() ?: JSONObject.NULL)
                        put("senderKey", m.senderPerson?.key ?: JSONObject.NULL)
                        put("senderUri", m.senderPerson?.uri ?: JSONObject.NULL)
                        put("dataMimeType", m.dataMimeType ?: JSONObject.NULL)
                        put("dataUri", m.dataUri?.toString() ?: JSONObject.NULL)
                    }
                )
            }
            return arr
        }

        // Fallback manual: os elementos são Bundles com as chaves conhecidas.
        for (p in raw) {
            val b = p as? Bundle ?: continue
            arr.put(
                JSONObject().apply {
                    put("text", b.getCharSequence("text")?.toString() ?: "")
                    put("timestamp", b.getLong("time"))
                    put("senderName", b.getCharSequence("sender")?.toString() ?: JSONObject.NULL)
                    put("senderKey", JSONObject.NULL)
                    put("senderUri", JSONObject.NULL)
                    put("dataMimeType", b.getString("type") ?: JSONObject.NULL)
                    put("dataUri", b.getParcelable<Parcelable>("uri")?.toString() ?: JSONObject.NULL)
                    put("raw_bundle", true)
                }
            )
        }
        return arr
    }

    private fun buildMessages(
        event: CapturedEvent,
        array: JSONArray,
        conversation: String,
        isGroup: Boolean,
    ): List<ParsedMessage> {
        val out = mutableListOf<ParsedMessage>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val text = o.optString("text", "")
            val timestamp = o.optLong("timestamp", event.postTime)
            out += ParsedMessage(
                eventId = 0, // preenchido pelo Repository após inserir o evento
                sbnKey = event.sbnKey,
                conversation = conversation,
                isGroup = isGroup,
                senderName = o.optStringOrNull("senderName"),
                senderKey = o.optStringOrNull("senderKey"),
                senderUri = o.optStringOrNull("senderUri"),
                text = text,
                timestamp = timestamp,
                mimeType = o.optStringOrNull("dataMimeType"),
                dataUri = o.optStringOrNull("dataUri"),
                dedupeKey = "${event.sbnKey}|$timestamp|$text",
            )
        }
        return out
    }

    // ---- ações ----

    /** Apenas registra as ações; nunca dispara nenhuma. */
    private fun actionsJson(n: Notification): JSONArray {
        val arr = JSONArray()
        val actions = safe { n.actions } ?: return arr
        for (a in actions) {
            if (a == null) continue
            arr.put(
                JSONObject().apply {
                    put("title", a.title?.toString() ?: JSONObject.NULL)
                    val inputs = safe { a.remoteInputs }
                    put("hasRemoteInput", inputs != null && inputs.isNotEmpty())
                    put(
                        "remoteInputKeys",
                        JSONArray().apply { inputs?.forEach { put(it.resultKey) } }
                    )
                    put(
                        "semanticAction",
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) safe { a.semanticAction } ?: 0 else 0
                    )
                    put(
                        "isContextual",
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) safe { a.isContextual } ?: false else false
                    )
                }
            )
        }
        return arr
    }

    // ---- helpers ----

    private fun hasLargeIcon(n: Notification, extras: Bundle): Boolean =
        safe {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && n.getLargeIcon() != null) return@safe true
            @Suppress("DEPRECATION")
            extras.get(Notification.EXTRA_LARGE_ICON) != null
        } ?: false

    private fun textLines(extras: Bundle): String? {
        val lines = safe { extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) } ?: return null
        if (lines.isEmpty()) return null
        return JSONArray().apply { lines.forEach { put(it?.toString() ?: JSONObject.NULL) } }.toString()
    }

    private fun str(extras: Bundle, key: String): String? =
        safe { extras.getCharSequence(key)?.toString() }?.takeIf { it.isNotEmpty() }

    private fun bool(extras: Bundle, key: String): Boolean? =
        safe { if (extras.containsKey(key)) extras.getBoolean(key) else null }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    /** Executa [block] devolvendo null em qualquer falha. */
    private inline fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            null
        }
}
