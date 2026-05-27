package com.neapp.tracker.mqtt

import android.content.Context
import android.location.Location
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.neapp.tracker.delivery.DeliveryOfferManager
import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.model.DeliveryOffer
import com.neapp.tracker.model.LocationPayload
import com.neapp.tracker.scheduler.SchedulerManager
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object MqttManager {

    private const val HOST = "sys.fastvistos.com.br"
    private const val PORT = 8883

    private var client: Mqtt3AsyncClient? = null
    private val gson = Gson()

    fun connect(context: Context, driverId: String) {
        val appContext = context.applicationContext

        client = MqttClient.builder()
            .useMqttVersion3()
            .serverHost(HOST)
            .serverPort(PORT)
            .identifier("android-$driverId-${System.currentTimeMillis()}")
            .sslWithDefaultConfig()
            .automaticReconnect()
            .initialDelay(1, TimeUnit.SECONDS)
            .maxDelay(30, TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .buildAsync()

        client?.connectWith()
            ?.keepAlive(60)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable == null) {
                    subscribeToConfig(appContext, driverId)
                    subscribeToDelivery(appContext, driverId)
                }
            }
    }

    private fun subscribeToConfig(context: Context, driverId: String) {
        client?.subscribeWith()
            ?.topicFilter("drivers/$driverId/config")
            ?.callback { publish ->
                val json = String(publish.payloadAsBytes)
                try {
                    val newConfig = gson.fromJson(json, DeliveryConfig::class.java)
                    saveAndReschedule(context, newConfig)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ?.send()
    }

    private fun subscribeToDelivery(context: Context, driverId: String) {
        client?.subscribeWith()
            ?.topicFilter("drivers/$driverId/delivery")
            ?.callback { publish ->
                val json = String(publish.payloadAsBytes)
                try {
                    val offer = gson.fromJson(json, DeliveryOffer::class.java)
                    DeliveryOfferManager.onOfferReceived(context, offer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ?.send()
    }

    private fun saveAndReschedule(context: Context, config: DeliveryConfig) {
        context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("shift_start", config.shiftStart)
            .putString("shift_end", config.shiftEnd)
            .putInt("moving_interval_seconds", config.movingIntervalSeconds)
            .putInt("idle_interval_seconds", config.idleIntervalSeconds)
            .apply()
        SchedulerManager.cancelAll(context)
        SchedulerManager.schedule(context, config)
    }

    fun publish(driverId: String, location: Location) {
        val payload = LocationPayload(
            id = driverId,
            lat = location.latitude,
            lng = location.longitude,
            ts = System.currentTimeMillis() / 1000,
            speed = location.speed
        )

        val bytes = Json.Default.encodeToString(LocationPayload.serializer(), payload)
            .toByteArray()

        client?.publishWith()
            ?.topic("drivers/$driverId/location")
            ?.payload(bytes)
            ?.send()
    }

    fun disconnect() {
        client?.disconnect()
        client = null
    }

    fun isConnected(): Boolean {
        return client?.state?.isConnected == true
    }
}