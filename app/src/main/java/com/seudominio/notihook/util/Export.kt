package com.seudominio.notihook.util

import com.seudominio.notihook.data.CapturedEvent
import com.seudominio.notihook.data.ParsedMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializa os dados para exportação. Campos que já guardam JSON (extrasJson,
 * messagesJson, actionsJson) são reinseridos como objetos, não como string
 * escapada, para o arquivo final ser legível.
 */
object Export {

    fun buildJson(events: List<CapturedEvent>, messages: List<ParsedMessage>): String =
        JSONObject().apply {
            put("app", "NotiHook")
            put("exportedAt", System.currentTimeMillis())
            put("eventCount", events.size)
            put("messageCount", messages.size)
            put("events", JSONArray().apply { events.forEach { put(eventToJson(it)) } })
            put("messages", JSONArray().apply { messages.forEach { put(messageToJson(it)) } })
        }.toString(2)

    fun eventToJson(e: CapturedEvent): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("capturedAt", e.capturedAt)
        put("eventType", e.eventType)
        put("removalReason", e.removalReason ?: JSONObject.NULL)
        put("sbnKey", e.sbnKey)
        put("packageName", e.packageName)
        put("notifId", e.notifId)
        put("tag", e.tag ?: JSONObject.NULL)
        put("postTime", e.postTime)
        put("groupKey", e.groupKey ?: JSONObject.NULL)
        put("userHandle", e.userHandle)
        put("isOngoing", e.isOngoing)
        put("isClearable", e.isClearable)
        put("flags", e.flags)
        put("category", e.category ?: JSONObject.NULL)
        put("channelId", e.channelId ?: JSONObject.NULL)
        put("group", e.group ?: JSONObject.NULL)
        put("sortKey", e.sortKey ?: JSONObject.NULL)
        put("shortcutId", e.shortcutId ?: JSONObject.NULL)
        put("when", e.whenTime)
        put("title", e.title ?: JSONObject.NULL)
        put("text", e.text ?: JSONObject.NULL)
        put("bigText", e.bigText ?: JSONObject.NULL)
        put("subText", e.subText ?: JSONObject.NULL)
        put("summaryText", e.summaryText ?: JSONObject.NULL)
        put("infoText", e.infoText ?: JSONObject.NULL)
        put("textLines", parseArrayOrNull(e.textLines))
        put("template", e.template ?: JSONObject.NULL)
        put("conversationTitle", e.conversationTitle ?: JSONObject.NULL)
        put("isGroupConversation", e.isGroupConversation ?: JSONObject.NULL)
        put("messages", parseArrayOrNull(e.messagesJson))
        put("historicMessages", parseArrayOrNull(e.historicMessagesJson))
        put("actions", parseArrayOrNull(e.actionsJson))
        put("hasLargeIcon", e.hasLargeIcon)
        put("hasSmallIcon", e.hasSmallIcon)
        put("isSummary", e.isSummary)
        put("extras", parseObjectOrNull(e.extrasJson))
    }

    private fun messageToJson(m: ParsedMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("eventId", m.eventId)
        put("sbnKey", m.sbnKey)
        put("conversation", m.conversation)
        put("isGroup", m.isGroup)
        put("senderName", m.senderName ?: JSONObject.NULL)
        put("senderKey", m.senderKey ?: JSONObject.NULL)
        put("senderUri", m.senderUri ?: JSONObject.NULL)
        put("text", m.text)
        put("timestamp", m.timestamp)
        put("mimeType", m.mimeType ?: JSONObject.NULL)
        put("dataUri", m.dataUri ?: JSONObject.NULL)
    }

    private fun parseArrayOrNull(raw: String?): Any =
        if (raw.isNullOrBlank()) JSONObject.NULL
        else try {
            JSONArray(raw)
        } catch (t: Throwable) {
            raw
        }

    private fun parseObjectOrNull(raw: String?): Any =
        if (raw.isNullOrBlank()) JSONObject.NULL
        else try {
            JSONObject(raw)
        } catch (t: Throwable) {
            raw
        }

    fun suggestedFileName(): String = "notihook-export-${System.currentTimeMillis()}.json"
}
