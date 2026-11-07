package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.repository.PreferencesRepository
import com.example.presentation.navigation.MainAppContainer
import com.example.scheduler.ReminderNotification
import com.example.ui.theme.CallMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize reminder notification channel
        ReminderNotification.createNotificationChannel(this)

        val preferencesRepository = PreferencesRepository(this)

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState()
            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            CallMateTheme(darkTheme = isDarkTheme) {
                MainAppContainer(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
