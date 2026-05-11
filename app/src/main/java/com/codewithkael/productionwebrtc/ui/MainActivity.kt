package com.codewithkael.productionwebrtc.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codewithkael.productionwebrtc.ui.theme.ProductionWebRTC
import com.codewithkael.productionwebrtc.ui.screens.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProductionWebRTC {
                MainScreen()
            }
        }
    }
}