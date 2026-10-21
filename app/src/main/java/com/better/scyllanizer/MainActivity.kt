package com.better.scyllanizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.better.scyllanizer.ui.ScyllaNizerApp
import com.better.scyllanizer.ui.theme.FileFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileFlowTheme {
                ScyllaNizerApp()
            }
        }
    }
}
