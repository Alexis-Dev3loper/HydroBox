package com.hydrobox.app.ui.model

import com.hydrobox.app.api.ApiAutomationAction
import com.hydrobox.app.api.ApiAutomationSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AutomationPresentationTest {
    private val zone = ZoneId.of("America/Mexico_City")

    @Test
    fun buildsDailySetStateWithInternalTimezoneAndGrace() {
        val draft = buildAutomationDraft(
            baseInput(
                actionChoice = AutomationActionChoice.SET_STATE,
                actuatorKey = "fan",
                scheduleChoice = AutomationScheduleChoice.DAILY,
                time = "7:05"
            ),
            zone
        )

        assertEquals("Ventilación", draft.name)
        assertEquals(ApiAutomationAction.SetState("fan", true), draft.action)
        assertEquals(
            ApiAutomationSchedule.Daily("07:05:00", "America/Mexico_City", 300),
            draft.schedule
        )
    }

    @Test
    fun nutrientDoseUsesMappedActuatorAndShortGrace() {
        val draft = buildAutomationDraft(
            baseInput(
                actionChoice = AutomationActionChoice.NUTRIENT_DOSE,
                nutrientKey = "flora_grow",
                nutrientActuatorKey = "flora_grow_pump",
                amountMl = "12,5",
                scheduleChoice = AutomationScheduleChoice.WEEKDAYS,
                isoWeekdays = setOf(5, 1, 3)
            ),
            zone
        )

        assertEquals(
            ApiAutomationAction.NutrientDose("flora_grow_pump", "flora_grow", 12.5),
            draft.action
        )
        assertEquals(
            ApiAutomationSchedule.Weekdays(
                "08:00:00",
                "America/Mexico_City",
                listOf(1, 3, 5),
                30
            ),
            draft.schedule
        )
    }

    @Test
    fun onceConvertsLocalDateAndTimeToInstant() {
        val draft = buildAutomationDraft(
            baseInput(
                actionChoice = AutomationActionChoice.RUN_FOR,
                actuatorKey = "water_pump",
                durationSeconds = "90",
                scheduleChoice = AutomationScheduleChoice.ONCE,
                date = "2026-09-22",
                time = "18:30"
            ),
            zone
        )

        val schedule = draft.schedule as ApiAutomationSchedule.Once
        assertEquals(Instant.parse("2026-09-23T00:30:00Z"), schedule.onceAt)
        assertEquals(300, schedule.misfireGraceSeconds)
    }

    @Test
    fun rejectsMissingTargetsInvalidTimesAndEmptyWeekdays() {
        val noActuator = assertThrows(IllegalArgumentException::class.java) {
            buildAutomationDraft(baseInput(actuatorKey = null), zone)
        }
        assertTrue(noActuator.message!!.contains("actuador"))

        val badTime = assertThrows(IllegalArgumentException::class.java) {
            buildAutomationDraft(baseInput(time = "25:00"), zone)
        }
        assertTrue(badTime.message!!.contains("HH:mm"))

        val noDays = assertThrows(IllegalArgumentException::class.java) {
            buildAutomationDraft(
                baseInput(
                    scheduleChoice = AutomationScheduleChoice.WEEKDAYS,
                    isoWeekdays = emptySet()
                ),
                zone
            )
        }
        assertTrue(noDays.message!!.contains("día"))
    }

    private fun baseInput(
        actionChoice: AutomationActionChoice = AutomationActionChoice.SET_STATE,
        actuatorKey: String? = "fan",
        nutrientKey: String? = null,
        nutrientActuatorKey: String? = null,
        targetState: Boolean = true,
        durationSeconds: String = "60",
        amountMl: String = "10",
        scheduleChoice: AutomationScheduleChoice = AutomationScheduleChoice.DAILY,
        date: String = "2026-09-22",
        time: String = "08:00",
        isoWeekdays: Set<Int> = setOf(1)
    ) = AutomationDraftInput(
        name = "  Ventilación  ",
        actionChoice = actionChoice,
        actuatorKey = actuatorKey,
        targetState = targetState,
        durationSeconds = durationSeconds,
        nutrientKey = nutrientKey,
        nutrientActuatorKey = nutrientActuatorKey,
        amountMl = amountMl,
        scheduleChoice = scheduleChoice,
        date = date,
        time = time,
        isoWeekdays = isoWeekdays
    )
}
