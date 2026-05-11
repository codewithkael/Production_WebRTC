package com.codewithkael.productionwebrtc.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codewithkael.productionwebrtc.R
import com.codewithkael.productionwebrtc.remote.firebase.FirebaseClient
import com.codewithkael.productionwebrtc.remote.firebase.SignalDataModel
import com.codewithkael.productionwebrtc.remote.firebase.SignalDataModelTypes
import com.codewithkael.productionwebrtc.ui.MainActivity
import com.codewithkael.productionwebrtc.utils.MyApplication
import com.codewithkael.productionwebrtc.utils.MyApplication.Companion.TAG
import com.codewithkael.productionwebrtc.utils.webrt.MyPeerObserver
import com.codewithkael.productionwebrtc.utils.webrt.RTCAudioManager
import com.codewithkael.productionwebrtc.utils.webrt.RTCClient
import com.codewithkael.productionwebrtc.utils.webrt.RTCClientImpl
import com.codewithkael.productionwebrtc.utils.webrt.WebRTCFactory
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {

    @Inject
    lateinit var firebaseClient: FirebaseClient
    @Inject
    lateinit var webRTCFactory: WebRTCFactory
    @Inject
    lateinit var gson: Gson

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rtcClient: RTCClient? = null
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private val userID: String = MyApplication.UserID
    private var participantId: String = ""
    private var remoteSurface: SurfaceViewRenderer? = null
    private var remoteStream: MediaStream? = null

    val callState: MutableStateFlow<Boolean> = MutableStateFlow(false)

    //service section
    private lateinit var mainNotification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                CallServiceActions.START.name -> handleStartService()
                CallServiceActions.STOP.name -> handleStopService()
                else -> Unit
            }
        }
        return START_STICKY
    }

    private fun handleStartService() {
        if (!isServiceRunning) {
            isServiceRunning = true
            startServiceWithNotification()
            observeIncomingSignals()
        }
    }

    private fun handleStopService() {
        isServiceRunning = false
        rtcClient?.onDestroy()
        rtcClient = null
        firebaseClient.clear()
        webRTCFactory.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotifications()
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
    }

    private fun startServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                MAIN_NOTIFICATION_ID,
                mainNotification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(MAIN_NOTIFICATION_ID, mainNotification.build())
        }
    }

    @SuppressLint("NewApi")
    private fun createNotifications() {
        val callChannel = NotificationChannel(
            CALL_NOTIFICATION_CHANNEL_ID,
            CALL_NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(callChannel)
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), contentIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationChannel = NotificationChannel(
            "call_service_channel",
            "Call Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(notificationChannel)

        val exitIntent = Intent(this, CallBroadcastReceiver::class.java).apply {
            action = "ACTION_EXIT"
        }
        val pendingExitIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE)

        mainNotification = NotificationCompat.Builder(this, "call_service_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WebRTC Call")
            .setContentText("Call is active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .addAction(R.mipmap.ic_launcher, "Exit", pendingExitIntent)
            .setContentIntent(contentPendingIntent)
    }

    // WebRTC logic moved from ViewModel
    private fun observeIncomingSignals() {
        firebaseClient.observeIncomingSignals { signalDataModel ->
            when (signalDataModel.type) {
                SignalDataModelTypes.INCOMING_CALL -> handleIncomingCall(signalDataModel)
                SignalDataModelTypes.ACCEPT_CALL -> handleAcceptCall()
                SignalDataModelTypes.OFFER -> handleReceivedOfferSdp(signalDataModel)
                SignalDataModelTypes.ANSWER -> handleReceivedAnswerSdp(signalDataModel)
                SignalDataModelTypes.ICE -> handleReceivedIceCandidate(signalDataModel)
                null -> Unit
            }
        }
    }

    private fun handleAcceptCall() {
        setupRtcConnection(participantId)?.also {
            it.offer()
        }
    }

    fun sendStartCallSignal(participantId: String) {
        this.participantId = participantId
        serviceScope.launch {
            callState.emit(true)
        }
        serviceScope.launch {
            firebaseClient.updateParticipantDataModel(
                participantId = participantId, data = SignalDataModel(
                    type = SignalDataModelTypes.INCOMING_CALL, participantId = userID
                )
            )
        }
    }

    private fun handleIncomingCall(dataModel: SignalDataModel) {
        this.participantId = dataModel.participantId
        serviceScope.launch {
            callState.emit(true)
        }
        serviceScope.launch {
            firebaseClient.updateParticipantDataModel(
                participantId = participantId, data = SignalDataModel(
                    type = SignalDataModelTypes.ACCEPT_CALL, participantId = userID
                )
            )
        }
    }

    private fun handleReceivedIceCandidate(signalDataModel: SignalDataModel) {
        runCatching {
            gson.fromJson(signalDataModel.data.toString(), IceCandidate::class.java)
        }.onSuccess {
            rtcClient?.onIceCandidateReceived(it)
        }.onFailure {
            Log.d(TAG, "handleReceivedIceCandidate: ${it.message}")
        }
    }

    private fun handleReceivedAnswerSdp(signalDataModel: SignalDataModel) {
        rtcClient?.onRemoteSessionReceived(
            SessionDescription(SessionDescription.Type.ANSWER, signalDataModel.data.toString())
        )
    }

    private fun handleReceivedOfferSdp(signalDataModel: SignalDataModel) {
        serviceScope.launch {
            callState.emit(true)
        }
        setupRtcConnection(participantId)?.also {
            it.onRemoteSessionReceived(
                SessionDescription(SessionDescription.Type.OFFER, signalDataModel.data.toString())
            )
            it.answer()
        }
    }

    private fun setupRtcConnection(participant: String): RTCClient? {
        runCatching { rtcClient?.onDestroy() }
        rtcClient = null
        rtcClient = webRTCFactory.createRTCClient(observer = object : MyPeerObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let {
                    rtcClient?.onLocalIceCandidateGenerated(it)
                }
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.let {
                    remoteStream = it
                    runCatching {
                        Log.d(TAG, "onAddStream: $it")
                        remoteSurface?.let { surface ->
                            it.videoTracks[0]?.addSink(surface)
                        } ?: run {
                            Log.d(TAG, "onAddStream: null surface")
                        }
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    serviceScope.launch {
                        firebaseClient.removeSelfData()
                    }
                }
            }
        }, listener = object : RTCClientImpl.TransferDataToServerCallback {
            override fun onIceGenerated(iceCandidate: IceCandidate) {
                serviceScope.launch {
                    firebaseClient.updateParticipantDataModel(
                        participantId = participant, data = SignalDataModel(
                            type = SignalDataModelTypes.ICE,
                            data = gson.toJson(iceCandidate),
                            participantId = userID
                        )
                    )
                }
            }

            override fun onOfferGenerated(sessionDescription: SessionDescription) {
                serviceScope.launch {
                    firebaseClient.updateParticipantDataModel(
                        participantId = participant, data = SignalDataModel(
                            type = SignalDataModelTypes.OFFER,
                            data = sessionDescription.description,
                            participantId = userID
                        )
                    )
                }
            }

            override fun onAnswerGenerated(sessionDescription: SessionDescription) {
                serviceScope.launch {
                    firebaseClient.updateParticipantDataModel(
                        participantId = participant, data = SignalDataModel(
                            type = SignalDataModelTypes.ANSWER,
                            data = sessionDescription.description,
                            participantId = userID
                        )
                    )
                }
            }
        })
        return rtcClient
    }

    fun startLocalStream(surface: SurfaceViewRenderer) {
        webRTCFactory.prepareLocalStream(surface)
    }

    fun initRemoteSurfaceView(remoteSurface: SurfaceViewRenderer) {
        this.remoteSurface = remoteSurface
        webRTCFactory.initSurfaceView(remoteSurface)
        remoteStream?.let {
            it.videoTracks[0]?.addSink(remoteSurface)
        }
    }

    fun switchCamera() = webRTCFactory.switchCamera()

    inner class CallServiceBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    private val binder = CallServiceBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    companion object {
        var isServiceRunning = false
        const val CALL_NOTIFICATION_CHANNEL_ID = "CALL_CHANNEL"
        const val MAIN_NOTIFICATION_ID = 2323

        fun startService(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallServiceActions.START.name
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallServiceActions.STOP.name
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
