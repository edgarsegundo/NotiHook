package com.seudominio.notihook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O SQL dos filtros é montado à mão, então vale travar o formato. */
class EventQueryTest {

    @Test
    fun `sem filtros ordena por capturedAt desc e limita`() {
        val q = Repository.buildEventQuery(EventFilter(limit = 50))

        assertFalse(q.sql.contains("WHERE"))
        assertTrue(q.sql.contains("ORDER BY capturedAt DESC"))
        assertTrue(q.sql.endsWith("LIMIT ?"))
        assertEquals(1, q.argCount)
    }

    @Test
    fun `busca gera quatro binds de LIKE`() {
        val q = Repository.buildEventQuery(EventFilter(query = "oi"))

        assertTrue(q.sql.contains("title LIKE ?"))
        assertTrue(q.sql.contains("extrasJson LIKE ?"))
        // 4 LIKE + limit
        assertEquals(5, q.argCount)
    }

    @Test
    fun `tipos de evento geram um placeholder por item`() {
        val q = Repository.buildEventQuery(
            EventFilter(eventTypes = setOf(EventType.POSTED, EventType.REMOVED))
        )

        assertTrue(q.sql.contains("eventType IN (?,?)"))
        assertEquals(3, q.argCount)
    }

    @Test
    fun `ocultar ongoing e resumos entra como condicao fixa`() {
        val q = Repository.buildEventQuery(EventFilter(hideOngoing = true, hideSummaries = true))

        assertTrue(q.sql.contains("isOngoing = 0"))
        assertTrue(q.sql.contains("isSummary = 0"))
        assertEquals(1, q.argCount)
    }

    @Test
    fun `channel e pacote entram como bind args`() {
        val q = Repository.buildEventQuery(
            EventFilter(channelId = "ch", packageName = "com.whatsapp.w4b")
        )

        assertTrue(q.sql.contains("channelId = ?"))
        assertTrue(q.sql.contains("packageName = ?"))
        assertEquals(3, q.argCount)
    }

    @Test
    fun `filtros combinados sao unidos por AND`() {
        val q = Repository.buildEventQuery(
            EventFilter(query = "x", hideOngoing = true, channelId = "ch")
        )

        assertTrue(q.sql.contains(" AND "))
    }
}
