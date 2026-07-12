package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.database.JarvisDatabase
import com.example.data.repository.JarvisRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.JarvisMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 1. Initialize persistent SQLite database & components
        val database = JarvisDatabase.getDatabase(applicationContext)
        val repository = JarvisRepository(
            noteDao = database.noteDao(),
            reminderDao = database.reminderDao(),
            commandLogDao = database.commandLogDao()
        )

        // 2. Instantiate Main ViewModel
        viewModel = MainViewModelFactory(repository).create(MainViewModel::class.java)

        // 3. Initialize Jarvis voice systems
        viewModel.initVoiceManager(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JarvisMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop any active Jarvis voice feedback if the app goes to background
        if (::viewModel.isInitialized) {
            viewModel.stopSpeaking()
        }
    }
}
