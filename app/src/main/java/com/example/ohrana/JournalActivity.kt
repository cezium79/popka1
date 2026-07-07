package com.example.ohrana

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class JournalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Устанавливаем горизонтальную ориентацию только для этого экрана
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        setContent {
            MaterialTheme {
                JournalScreen(onBack = { finish() })
            }
        }
    }
    
    companion object {
        fun newIntent(context: android.content.Context): Intent {
            return Intent(context, JournalActivity::class.java)
        }
    }
}
