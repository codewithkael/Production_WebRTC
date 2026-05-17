package com.codewithkael.productionwebrtc.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.productionwebrtc.service.CallService
import com.codewithkael.productionwebrtc.service.ConnectionState
import com.codewithkael.productionwebrtc.utils.webrt.RTCStatsModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var callService: CallService? = null
    private var isBound = false

    private val _callState = MutableStateFlow(false)
    val callState = _callState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _rtcStats = MutableStateFlow<RTCStatsModel?>(null)
    val rtcStats = _rtcStats.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CallService.CallServiceBinder
            callService = binder.getService()
            isBound = true
            observeServiceStates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            callService = null
            isBound = false
        }
    }

    fun initService(context: Context) {
        CallService.startService(context)
        Intent(context, CallService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeServiceStates() {
        viewModelScope.launch {
            callService?.callState?.collectLatest {
                _callState.emit(it)
            }
        }
        viewModelScope.launch {
            callService?.connectionState?.collectLatest {
                _connectionState.emit(it)
            }
        }
        viewModelScope.launch {
            callService?.statsFlow?.collectLatest {
                _rtcStats.emit(it)
            }
        }
    }

    fun sendStartCallSignal(participantId: String) {
        callService?.sendStartCallSignal(participantId)
    }

    fun startLocalStream(surface: SurfaceViewRenderer) {
        callService?.startLocalStream(surface)
    }

    fun initRemoteSurfaceView(remoteSurface: SurfaceViewRenderer) {
        callService?.initRemoteSurfaceView(remoteSurface)
    }

    fun switchCamera() {
        callService?.switchCamera()
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
