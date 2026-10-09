package com.seudominio.notihook.capture

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.widget.RemoteViews
import androidx.core.app.Person
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dump completo e defensivo de um [Bundle] de extras de notificação.
 *
 * Cada chave vira `{ "type": "<classe do valor>", "value": ... }`. Nada aqui
 * pode lançar: o serviço não pode crashar por causa de um extra inesperado.
 */
object BundleDump {

    const val MAX_DEPTH = 5
    const val MAX_STRING_LENGTH = 20_000

    private const val NOT_SERIALIZABLE = "<não serializável>"

    fun bundleToJson(b: Bundle, depth: Int = 0): JSONObject {
        val out = JSONObject()
        if (depth >= MAX_DEPTH) {
            out.put("_depth_limit", true)
            return out
        }
        val keys = try {
            b.keySet()
        } catch (t: Throwable) {
            out.put("_keyset_error", t.toString())
            return out
        }
        for (key in keys) {
            out.put(key, describe(readValue(b, key), depth))
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun readValue(b: Bundle, key: String): Any? =
        try {
            b.get(key)
        } catch (t: Throwable) {
            ReadError(t.toString())
        }

    private class ReadError(val message: String)

    /** Monta o objeto `{type, value}` de um único valor. */
    private fun describe(value: Any?, depth: Int): JSONObject {
        val node = JSONObject()
        if (value is ReadError) {
            node.put("type", "error")
            node.put("value", value.message)
            return node
        }
        if (value == null) {
            node.put("type", "null")
            node.put("value", JSONObject.NULL)
            return node
        }
        node.put("type", typeNameOf(value))
        try {
            when (value) {
                is Bundle -> node.put("value", bundleToJson(value, depth + 1))

                is Boolean, is Int, is Long, is Short, is Byte, is Float, is Double, is Char ->
                    node.put("value", value.toString())

                is CharSequence -> putString(node, value.toString())

                is Icon, is PendingIntent, is Bitmap, is RemoteViews ->
                    node.put("value", NOT_SERIALIZABLE)

                is Person -> node.put("value", personToJson(value))
                is android.app.Person -> node.put("value", personToJson(Person.fromAndroidPerson(value)))

                is BooleanArray -> node.put("value", JSONArray().apply { value.forEach { put(it) } })
                is IntArray -> node.put("value", JSONArray().apply { value.forEach { put(it) } })
                is LongArray -> node.put("value", JSONArray().apply { value.forEach { put(it) } })
                is DoubleArray -> node.put("value", JSONArray().apply { value.forEach { put(it) } })
                is FloatArray -> node.put("value", JSONArray().apply { value.forEach { put(it.toDouble()) } })
                is CharArray -> putString(node, String(value))
                is ByteArray -> node.put("value", "<${value.size} bytes>")

                is Array<*> -> node.put("value", arrayToJson(value, depth))
                is Iterable<*> -> node.put("value", arrayToJson(value.toList().toTypedArray(), depth))

                else -> putString(node, value.toString())
            }
        } catch (t: Throwable) {
            node.put("value", "<erro: $t>")
        }
        return node
    }

    /**
     * Arrays e listas. Elementos Bundle (caso de EXTRA_MESSAGES) e Parcelable
     * que sejam Bundle entram por recursão.
     */
    private fun arrayToJson(array: Array<*>, depth: Int): JSONArray {
        val arr = JSONArray()
        if (depth + 1 >= MAX_DEPTH) {
            arr.put("<limite de profundidade>")
            return arr
        }
        for (element in array) {
            when (element) {
                null -> arr.put(JSONObject.NULL)
                is Bundle -> arr.put(bundleToJson(element, depth + 1))
                is CharSequence -> arr.put(truncate(element.toString()).first)
                is Boolean, is Int, is Long, is Short, is Byte, is Float, is Double ->
                    arr.put(element.toString())
                is Person -> arr.put(personToJson(element))
                is android.app.Person -> arr.put(personToJson(Person.fromAndroidPerson(element)))
                is Parcelable -> arr.put(describe(element, depth + 1))
                else -> arr.put(try { element.toString() } catch (t: Throwable) { "<erro: $t>" })
            }
        }
        return arr
    }

    private fun personToJson(p: Person): JSONObject = JSONObject().apply {
        put("name", p.name?.toString() ?: JSONObject.NULL)
        put("key", p.key ?: JSONObject.NULL)
        put("uri", p.uri ?: JSONObject.NULL)
        put("isBot", p.isBot)
        put("isImportant", p.isImportant)
    }

    /** Grava a string truncada, marcando `truncated` quando passa do limite. */
    private fun putString(node: JSONObject, raw: String) {
        val (text, truncated) = truncate(raw)
        node.put("value", text)
        if (truncated) node.put("truncated", true)
    }

    private fun truncate(raw: String): Pair<String, Boolean> =
        if (raw.length > MAX_STRING_LENGTH) raw.take(MAX_STRING_LENGTH) to true else raw to false

    private fun typeNameOf(value: Any): String =
        try {
            value.javaClass.name
        } catch (t: Throwable) {
            "unknown"
        }

    /** Nomes legíveis das flags de Notification, para a UI de detalhe. */
    fun decodeFlags(flags: Int): List<String> {
        val names = linkedMapOf(
            "SHOW_LIGHTS" to 1, // FLAG_SHOW_LIGHTS, deprecado mas ainda presente nos flags
            "ONGOING_EVENT" to Notification.FLAG_ONGOING_EVENT,
            "INSISTENT" to Notification.FLAG_INSISTENT,
            "ONLY_ALERT_ONCE" to Notification.FLAG_ONLY_ALERT_ONCE,
            "AUTO_CANCEL" to Notification.FLAG_AUTO_CANCEL,
            "NO_CLEAR" to Notification.FLAG_NO_CLEAR,
            "FOREGROUND_SERVICE" to Notification.FLAG_FOREGROUND_SERVICE,
            "GROUP_SUMMARY" to Notification.FLAG_GROUP_SUMMARY,
            "LOCAL_ONLY" to Notification.FLAG_LOCAL_ONLY,
            "BUBBLE" to 4096, // FLAG_BUBBLE, API 29+
        )
        return names.filter { (_, bit) -> flags and bit != 0 }.keys.toList()
    }
}
