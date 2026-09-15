package com.hydrobox.app.mqtt

/** Characterized legacy contract. Do not extend; MB-004 replaces it with API commands. */
object LegacyMqttContract {
    fun actuatorTopic(deviceId: String): String {
        require(deviceId.isNotBlank()) { "legacy MQTT device id is required" }
        return "hydrobox/actuators/$deviceId/set"
    }

    fun switchPayload(on: Boolean): String = "{\"on\":$on}"

    fun dosingPayload(amountMl: Int): String {
        require(amountMl > 0) { "legacy dosing amount must be positive" }
        return "{\"dose_ml\":$amountMl}"
    }
}
