package com.hydrobox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hydrobox.app.ui.navigation.HydroNavRoot
import com.hydrobox.app.ui.theme.HydroBoxTheme
import com.hydrobox.app.ui.theme.LocalDarkThemeState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val darkState = rememberSaveable { mutableStateOf(true) }

            CompositionLocalProvider(LocalDarkThemeState provides darkState) {
                HydroBoxTheme(
                    darkTheme = darkState.value,
                    dynamicColor = false
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HydroNavRoot()
                    }
                }
            }
        }
    }
}

