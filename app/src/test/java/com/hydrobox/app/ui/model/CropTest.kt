package com.hydrobox.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropTest {
    @Test
    fun resolvesEveryCanonicalCropKeyWithoutLegacyNumericIds() {
        val expected = mapOf(
            "lettuce" to Crop.LECHUGA,
            "spinach" to Crop.ESPINACA,
            "chard" to Crop.ACELGA,
            "arugula" to Crop.RUCULA,
            "basil" to Crop.ALBAHACA,
            "mustard" to Crop.MOSTAZA
        )

        assertEquals(expected, expected.keys.associateWith(Crop::fromKey))
        assertNull(Crop.fromKey("1"))
        assertNull(Crop.fromKey("legacy_crop"))
    }
}
