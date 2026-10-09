package com.seudominio.notihook.capture

import android.os.Bundle
import androidx.core.app.Person
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Bundle e Person são classes do framework, daí o Robolectric. */
@RunWith(RobolectricTestRunner::class)
class BundleDumpTest {

    private fun JSONObject.valueOf(key: String): JSONObject = getJSONObject(key)

    @Test
    fun `tipos primitivos e strings saem como string`() {
        val b = Bundle().apply {
            putString("s", "olá")
            putInt("i", 42)
            putBoolean("flag", true)
            putLong("l", 123L)
        }
        val json = BundleDump.bundleToJson(b)

        assertEquals("olá", json.valueOf("s").getString("value"))
        assertEquals("42", json.valueOf("i").getString("value"))
        assertEquals("true", json.valueOf("flag").getString("value"))
        assertEquals("123", json.valueOf("l").getString("value"))
        assertEquals("java.lang.String", json.valueOf("s").getString("type"))
    }

    @Test
    fun `bundle aninhado entra por recursao`() {
        val inner = Bundle().apply { putString("dentro", "valor") }
        val b = Bundle().apply { putBundle("outer", inner) }

        val json = BundleDump.bundleToJson(b)
        val nested = json.valueOf("outer").getJSONObject("value")

        assertEquals("valor", nested.valueOf("dentro").getString("value"))
    }

    @Test
    fun `profundidade para no limite sem estourar a pilha`() {
        // Encadeia mais bundles do que MAX_DEPTH permite.
        var current = Bundle().apply { putString("fundo", "x") }
        repeat(BundleDump.MAX_DEPTH + 3) {
            current = Bundle().apply { putBundle("n", current) }
        }

        val json = BundleDump.bundleToJson(current)

        // Desce até achar a marca de limite.
        var node = json
        var depthLimitFound = false
        repeat(BundleDump.MAX_DEPTH + 3) {
            val child = node.optJSONObject("n")?.optJSONObject("value") ?: return@repeat
            if (child.optBoolean("_depth_limit", false)) depthLimitFound = true
            node = child
        }
        assertTrue("esperava a marca _depth_limit", depthLimitFound)
    }

    @Test
    fun `string longa e truncada e marcada`() {
        val longText = "a".repeat(BundleDump.MAX_STRING_LENGTH + 500)
        val b = Bundle().apply { putString("big", longText) }

        val node = BundleDump.bundleToJson(b).valueOf("big")

        assertEquals(BundleDump.MAX_STRING_LENGTH, node.getString("value").length)
        assertTrue(node.getBoolean("truncated"))
    }

    @Test
    fun `string curta nao e marcada como truncada`() {
        val b = Bundle().apply { putString("small", "curta") }
        val node = BundleDump.bundleToJson(b).valueOf("small")

        assertFalse(node.has("truncated"))
    }

    @Test
    fun `tipo desconhecido cai no toString`() {
        class Esquisito {
            override fun toString() = "sou-esquisito"
        }
        // Serializable para poder entrar no Bundle.
        val b = Bundle().apply { putSerializable("odd", java.util.Date(0)) }
        val node = BundleDump.bundleToJson(b).valueOf("odd")

        assertNotNull(node.getString("value"))
        assertEquals("java.util.Date", node.getString("type"))
        assertEquals(Esquisito().toString(), "sou-esquisito")
    }

    @Test
    fun `arrays de primitivos e de strings viram json array`() {
        val b = Bundle().apply {
            putIntArray("ints", intArrayOf(1, 2, 3))
            putStringArray("strs", arrayOf("a", "b"))
        }
        val json = BundleDump.bundleToJson(b)

        assertEquals(3, json.valueOf("ints").getJSONArray("value").length())
        assertEquals("b", json.valueOf("strs").getJSONArray("value").getString(1))
    }

    @Test
    fun `array de bundles e expandido recursivamente`() {
        val m1 = Bundle().apply { putCharSequence("text", "primeira") }
        val m2 = Bundle().apply { putCharSequence("text", "segunda") }
        val b = Bundle().apply { putParcelableArray("android.messages", arrayOf(m1, m2)) }

        val arr = BundleDump.bundleToJson(b).valueOf("android.messages").getJSONArray("value")

        assertEquals(2, arr.length())
        assertEquals(
            "primeira",
            arr.getJSONObject(0).getJSONObject("text").getString("value")
        )
    }

    @Test
    fun `person e serializado com seus campos`() {
        val person = Person.Builder()
            .setName("Maria")
            .setKey("chave-1")
            .setUri("tel:+5511999999999")
            .setBot(false)
            .setImportant(true)
            .build()
        val b = Bundle().apply { putParcelable("person", person.toAndroidPerson()) }

        val value = BundleDump.bundleToJson(b).valueOf("person").getJSONObject("value")

        assertEquals("Maria", value.getString("name"))
        assertEquals("chave-1", value.getString("key"))
        assertTrue(value.getBoolean("isImportant"))
        assertFalse(value.getBoolean("isBot"))
    }

    @Test
    fun `bundle vazio gera json vazio`() {
        assertEquals(0, BundleDump.bundleToJson(Bundle()).length())
    }

    @Test
    fun `decodeFlags nomeia as flags ativas`() {
        val flags = android.app.Notification.FLAG_GROUP_SUMMARY or
            android.app.Notification.FLAG_ONGOING_EVENT
        val decoded = BundleDump.decodeFlags(flags)

        assertTrue(decoded.contains("GROUP_SUMMARY"))
        assertTrue(decoded.contains("ONGOING_EVENT"))
        assertFalse(decoded.contains("AUTO_CANCEL"))
    }
}
