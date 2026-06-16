package com.codewithkael.productionwebrtc.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
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
    val rtcStats by viewModel.rtcStats.collectAsState()
    val isBlurEnabled by viewModel.isBlurEnabled.collectAsState()
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Background Blur: ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            RadioButton(
                selected = isBlurEnabled,
                onClick = { viewModel.toggleBlur(true) }
            )
            Text("On", fontSize = 14.sp, modifier = Modifier.padding(end = 16.dp))
            RadioButton(
                selected = !isBlurEnabled,
                onClick = { viewModel.toggleBlur(false) }
            )
            Text("Off", fontSize = 14.sp)
        }

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

                rtcStats?.let {
                    StatsOverlay(it)
                }

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

@Composable
fun StatsOverlay(stats: com.codewithkael.productionwebrtc.utils.webrt.RTCStatsModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text("📊 Live Metrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            MetricRow("Bitrate", "${stats.bitrate.toInt()} kbps")
            MetricRow("RTT", "${stats.rtt.toInt()} ms")
            MetricRow("Jitter", "%.3f s".format(stats.jitter))
            MetricRow("FPS", "${stats.frameRate}")
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text("$label: ", color = Color.LightGray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
