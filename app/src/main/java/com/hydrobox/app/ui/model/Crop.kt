package com.hydrobox.app.ui.model

import androidx.annotation.DrawableRes
import com.hydrobox.app.R

enum class Crop(
    val cropKey: String,
    @DrawableRes val imageRes: Int,
    val fallbackDisplayName: String,
    val fallbackCycleDays: Int
) {
    ACELGA("chard", R.drawable.crop_acelga, "Acelga", 28),
    ALBAHACA("basil", R.drawable.crop_albahaca, "Albahaca", 30),
    ESPINACA("spinach", R.drawable.crop_espinaca, "Espinaca", 32),
    LECHUGA("lettuce", R.drawable.crop_lechuga, "Lechuga", 45),
    MOSTAZA("mustard", R.drawable.crop_mostaza, "Mostaza", 26),
    RUCULA("arugula", R.drawable.crop_rucula, "Rúcula", 30);

    companion object {
        fun fromKey(cropKey: String): Crop? = entries.firstOrNull { it.cropKey == cropKey }
    }
}
