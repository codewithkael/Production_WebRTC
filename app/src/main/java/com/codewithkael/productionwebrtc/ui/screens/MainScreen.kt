package com.codewithkael.productionwebrtc.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.codewithkael.productionwebrtc.service.ConnectionState
import com.codewithkael.productionwebrtc.ui.components.CallControlsSection
import com.codewithkael.productionwebrtc.ui.components.FooterSection
import com.codewithkael.productionwebrtc.ui.components.TopBarSection
import com.codewithkael.productionwebrtc.ui.components.VideoStageSection
import com.codewithkael.productionwebrtc.ui.viewmodel.MainViewModel

@Composable
fun MainScreen() {

    val viewModel: MainViewModel = hiltViewModel()
    val callState by viewModel.callState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    // ---------- Permissions ----------
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(
                context, "Camera, Microphone and Notification permissions are required", Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.initService(context)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindService(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAEAEA))
            .padding(top = 14.dp)
    ) {

        TopBarSection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f),
        )
        CallControlsSection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onCall = { viewModel.sendStartCallSignal(it) })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(5f)
                .padding(8.dp)
        ) {
            if (callState) {
                VideoStageSection(
                    modifier = Modifier.fillMaxSize(),
                    inCall = true,
                    onRemoteReady = { viewModel.initRemoteSurfaceView(it) },
                    onLocalReady = { viewModel.startLocalStream(it) })

                if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                    val statusText = if (connectionState == ConnectionState.CONNECTING) "Connecting..." else "Reconnecting..."
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(50.dp),
                                color = Color.White
                            )
                            Text(
                                text = statusText,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black, RoundedCornerShape(12.dp))
                )
            }
        }

        FooterSection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 5.dp, vertical = 10.dp)
        )
    }
}
