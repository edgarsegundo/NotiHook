package com.neapp.tracker.delivery

import android.content.Context
import com.neapp.tracker.model.DeliveryOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.format.DateTimeParseException

object DeliveryOfferManager {

    private const val MAX_RECENT_IDS = 100

    private val recentEventIds = LinkedHashSet<String>()
    private val _latestOffer = MutableStateFlow<DeliveryOffer?>(null)
    val latestOffer: StateFlow<DeliveryOffer?> = _latestOffer.asStateFlow()

    @Synchronized
    fun onOfferReceived(context: Context, offer: DeliveryOffer) {
        if (isExpired(offer.expiresAt)) return
        if (recentEventIds.contains(offer.eventId)) return

        recentEventIds.add(offer.eventId)
        if (recentEventIds.size > MAX_RECENT_IDS) {
            val oldest = recentEventIds.firstOrNull()
            if (oldest != null) recentEventIds.remove(oldest)
        }

        _latestOffer.value = offer
        DeliveryNotificationManager.showOffer(context, offer)
    }

    private fun isExpired(expiresAt: String): Boolean {
        return try {
            Instant.parse(expiresAt).isBefore(Instant.now())
        } catch (_: DateTimeParseException) {
            // Payload inválido não deve gerar oferta ativa
            true
        }
    }
}
