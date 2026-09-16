package com.hydrobox.app.ui.model

import androidx.annotation.DrawableRes
import com.hydrobox.app.R

enum class Crop(
    val cropKey: String,
    @DrawableRes val imageRes: Int,
    val fallbackDisplayName: String
) {
    ACELGA("chard", R.drawable.crop_acelga, "Acelga"),
    ALBAHACA("basil", R.drawable.crop_albahaca, "Albahaca"),
    ESPINACA("spinach", R.drawable.crop_espinaca, "Espinaca"),
    LECHUGA("lettuce", R.drawable.crop_lechuga, "Lechuga"),
    MOSTAZA("mustard", R.drawable.crop_mostaza, "Mostaza"),
    RUCULA("arugula", R.drawable.crop_rucula, "Rúcula");

    companion object {
        fun fromKey(cropKey: String): Crop? = entries.firstOrNull { it.cropKey == cropKey }
    }
}
