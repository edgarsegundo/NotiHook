package com.neapp.tracker.mqtt

import android.location.Location
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.neapp.tracker.model.LocationPayload
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object MqttManager {

    private const val HOST = "sys.fastvistos.com.br"
    private const val PORT = 8883

    private var client: Mqtt3AsyncClient? = null

    fun connect(driverId: String) {
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