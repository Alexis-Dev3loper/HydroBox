package com.hydrobox.app.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyMqttContractTest {
    @Test
    fun switchContractPreservesTopicAndPayload() {
        assertEquals(
            "hydrobox/actuators/fan/set",
            LegacyMqttContract.actuatorTopic("fan")
        )
        assertEquals("{\"on\":true}", LegacyMqttContract.switchPayload(true))
        assertEquals("{\"on\":false}", LegacyMqttContract.switchPayload(false))
    }

    @Test
    fun dosingContractPreservesTopicAndPayload() {
        assertEquals(
            "hydrobox/actuators/flora_grow/set",
            LegacyMqttContract.actuatorTopic("flora_grow")
        )
        assertEquals("{\"dose_ml\":25}", LegacyMqttContract.dosingPayload(25))
    }

    @Test(expected = IllegalArgumentException::class)
    fun topicRejectsBlankLegacyDeviceId() {
        LegacyMqttContract.actuatorTopic(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun dosingRejectsNonPositiveAmount() {
        LegacyMqttContract.dosingPayload(0)
    }
}
