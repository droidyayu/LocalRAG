package com.ayushig.localrag.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ayushig.localrag.demo.debug.LaunchOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LaunchOptions.engineMode = intent?.getStringExtra("localrag.engine")
        setContent {
            App()
        }
    }
}
