package com.codewithkael.productionwebrtc.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codewithkael.productionwebrtc.R
import com.codewithkael.productionwebrtc.remote.firebase.FirebaseClient
import com.codewithkael.productionwebrtc.remote.firebase.SignalDataModel
import com.codewithkael.productionwebrtc.remote.firebase.SignalDataModelTypes
import com.codewithkael.productionwebrtc.ui.MainActivity
import com.codewithkael.productionwebrtc.utils.MyApplication
import com.codewithkael.productionwebrtc.utils.Tags
import com.codewithkael.productionwebrtc.utils.webrt.MyPeerObserver
import com.codewithkael.productionwebrtc.utils.webrt.RTCAudioManager
import com.codewithkael.productionwebrtc.utils.webrt.RTCClient
import com.codewithkael.productionwebrtc.utils.webrt.RTCClientImpl
import com.codewithkael.productionwebrtc.utils.webrt.RTCStatsModel
import com.codewithkael.productionwebrtc.utils.webrt.RTCStatsParser
import com.codewithkael.productionwebrtc.utils.webrt.WebRTCFactory
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
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

    private val TAG_WEBRTC = Tags.serviceTag
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rtcClient: RTCClient? = null
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private val userID: String = MyApplication.UserID
    private var participantId: String = ""
    private var remoteSurface: SurfaceViewRenderer? = null
    private var remoteStream: MediaStream? = null

    private var isCaller = false
    private var reconnectionJob: Job? = null
    private var fallbackReconnectionJob: Job? = null
    private var wasEstablished = false
    private var retryCount = 0
    private val statsParser = RTCStatsParser()
    private var statsJob: Job? = null

    val callState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.IDLE)
    val statsFlow: MutableStateFlow<RTCStatsModel?> = MutableStateFlow(null)

    //service section
    private lateinit var mainNotification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private lateinit var connectivityManager: ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG_WEBRTC, "🌐 [Network] -> Available. Proactively triggering restart if needed.")
            if (callState.value && connectionState.value != ConnectionState.CONNECTED) {
                startIceRestart()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG_WEBRTC, "⚙️ [Service] -> onStartCommand action: ${intent?.action}")
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
            Log.d(TAG_WEBRTC, "🚀 [Service] -> Starting CallService...")
            isServiceRunning = true
            startServiceWithNotification()
            observeIncomingSignals()
            registerNetworkCallback()
        }
    }

    private fun handleStopService() {
        Log.d(TAG_WEBRTC, "🛑 [Service] -> Stopping CallService...")
        isServiceRunning = false
        unregisterNetworkCallback()
        rtcClient?.onDestroy()
        rtcClient = null
        retryCount = 0
        isCaller = false
        wasEstablished = false
        participantId = ""
        remoteStream = null
        firebaseClient.clear()
        webRTCFactory.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG_WEBRTC, "✨ [Service] -> CallService Created")
        notificationManager = getSystemService(NotificationManager::class.java)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        createNotifications()
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
    }


    private fun startIceRestart() {
        if (!callState.value || rtcClient == null) return

        retryCount++
        Log.d(TAG_WEBRTC, "♻️ [Persistence] -> Attempt #$retryCount to reconnect...")

        if (retryCount > 3) {
            Log.e(TAG_WEBRTC, "❌ [Persistence] -> Max retries reached. Resetting call.")
            resetCallToInitialState("Call failed: Connection could not be restored.")
            return
        }

        serviceScope.launch {
            connectionState.emit(if (wasEstablished) ConnectionState.RECONNECTING else ConnectionState.CONNECTING)
            if (isCaller) {
                Log.d(TAG_WEBRTC, "♻️ [Persistence] -> Initiating ICE Restart Offer.")
                rtcClient?.offer(iceRestart = true)
            }
        }
        startFallbackTimer()
    }

    private fun resetCallToInitialState(message: String) {
        serviceScope.launch {
            callState.emit(false)
            connectionState.emit(ConnectionState.IDLE)

            rtcClient?.onDestroy()
            rtcClient = null
            retryCount = 0
            isCaller = false
            wasEstablished = false
            participantId = ""
            remoteStream = null

            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun unregisterNetworkCallback() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun startServiceWithNotification() {
        Log.d(TAG_WEBRTC, "🔔 [Service] -> startServiceWithNotification")
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
            "Call Channel",
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

    private fun observeIncomingSignals() {
        firebaseClient.observeIncomingSignals { signalDataModel ->
            Log.d(TAG_WEBRTC, "📩 [Signal] -> Received: ${signalDataModel.type} from ${signalDataModel.participantId}")
            when (signalDataModel.type) {
                SignalDataModelTypes.INCOMING_CALL -> handleIncomingCall(signalDataModel)
                SignalDataModelTypes.ACCEPT_CALL -> handleAcceptCall(signalDataModel)
                SignalDataModelTypes.OFFER -> handleReceivedOfferSdp(signalDataModel)
                SignalDataModelTypes.ANSWER -> handleReceivedAnswerSdp(signalDataModel)
                SignalDataModelTypes.ICE -> handleReceivedIceCandidate(signalDataModel)
                null -> Unit
            }
        }
    }

    private fun handleAcceptCall(signalDataModel: SignalDataModel) {
        Log.d(TAG_WEBRTC, "🤝 [Flow] -> Call Accepted by ${signalDataModel.participantId}. Initiating Handshake...")
        this.participantId = signalDataModel.participantId
        isCaller = true
        wasEstablished = false
        serviceScope.launch {
            connectionState.emit(ConnectionState.CONNECTING)
        }
        setupRtcConnection(participantId)?.also {
            it.offer()
        }
    }

    fun sendStartCallSignal(participantId: String) {
        Log.d(TAG_WEBRTC, "📞 [Flow] -> Starting Call with $participantId")
        this.participantId = participantId
        isCaller = true
        wasEstablished = false

        serviceScope.launch {
            callState.emit(true)
            connectionState.emit(ConnectionState.CONNECTING)
        }
        serviceScope.launch {
            firebaseClient.sendSignal(
                participantId = participantId, data = SignalDataModel(
                    type = SignalDataModelTypes.INCOMING_CALL, participantId = userID
                )
            )
        }
    }

    private fun handleIncomingCall(dataModel: SignalDataModel) {
        Log.d(TAG_WEBRTC, "🔔 [Flow] -> Incoming Call Request from ${dataModel.participantId}")
        this.participantId = dataModel.participantId
        isCaller = false
        wasEstablished = false
        serviceScope.launch {
            callState.emit(true)
            connectionState.emit(ConnectionState.CONNECTING)
        }
        serviceScope.launch {
            firebaseClient.sendSignal(
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
            Log.e(TAG_WEBRTC, "❌ [Signal] -> Error parsing ICE Candidate: ${it.message}")
        }
    }

    private fun handleReceivedAnswerSdp(signalDataModel: SignalDataModel) {
        val sdp = signalDataModel.data
        if (sdp.isNullOrEmpty()) {
            Log.e(TAG_WEBRTC, "❌ [Signal] -> Received ANSWER with empty SDP!")
            return
        }
        rtcClient?.onRemoteSessionReceived(
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun handleReceivedOfferSdp(signalDataModel: SignalDataModel) {
        val sdp = signalDataModel.data
        if (sdp.isNullOrEmpty()) {
            Log.e(TAG_WEBRTC, "❌ [Signal] -> Received OFFER with empty SDP!")
            return
        }
        this.participantId = signalDataModel.participantId
        serviceScope.launch {
            callState.emit(true)
            if (connectionState.value == ConnectionState.IDLE) {
                wasEstablished = false
                connectionState.emit(ConnectionState.CONNECTING)
            }
        }

        val client = rtcClient ?: setupRtcConnection(participantId)
        client?.onRemoteSessionReceived(
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
    }

    private fun setupRtcConnection(participant: String): RTCClient? {
        Log.d(TAG_WEBRTC, "🏗️ [Flow] -> Setting up PeerConnection for $participant")
        runCatching { rtcClient?.onDestroy() }
        rtcClient = null
        rtcClient = webRTCFactory.createRTCClient(observer = object : MyPeerObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                super.onIceCandidate(candidate)
                candidate?.let {
                    rtcClient?.onLocalIceCandidateGenerated(it)
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                super.onAddStream(stream)
                stream?.let {
                    remoteStream = it
                    if (it.videoTracks.isNotEmpty()) {
                        runCatching {
                            Log.d(TAG_WEBRTC, "📺 [Flow] -> Remote MediaStream received: ${it.id}")
                            remoteSurface?.let { surface ->
                                it.videoTracks[0]?.addSink(surface)
                            }
                        }
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                val track = transceiver?.receiver?.track()
                if (track is org.webrtc.VideoTrack) {
                    Log.d(TAG_WEBRTC, "📺 [Flow] -> Remote VideoTrack received via onTrack: ${track.id()}")
                    remoteSurface?.let { surface ->
                        track.addSink(surface)
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                Log.d(TAG_WEBRTC, "🔌 [Persistence] -> Connection State: $newState")
                when(newState) {
                    PeerConnection.PeerConnectionState.CONNECTING -> {
                        serviceScope.launch {
                            if (connectionState.value == ConnectionState.IDLE) {
                                connectionState.emit(ConnectionState.CONNECTING)
                            }
                        }
                        startFallbackTimer()
                    }
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        serviceScope.launch {
                            wasEstablished = true
                            connectionState.emit(ConnectionState.CONNECTED)
                            firebaseClient.removeSelfData()
                        }
                        retryCount = 0
                        reconnectionJob?.cancel()
                        fallbackReconnectionJob?.cancel()
                        startStatsPolling()
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        Log.w(TAG_WEBRTC, "⚠️ [Persistence] -> Connection Disconnected. Starting Reconnection Timer.")
                        fallbackReconnectionJob?.cancel()
                        stopStatsPolling()
                        startReconnectionTimer()
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        Log.e(TAG_WEBRTC, "❌ [Persistence] -> Connection Failed. Triggering immediate ICE Restart.")
                        fallbackReconnectionJob?.cancel()
                        stopStatsPolling()
                        startIceRestart()
                    }
                    else -> Unit
                }
            }
        }, listener = object : RTCClientImpl.TransferDataToServerCallback {
            override fun onIceGenerated(iceCandidate: IceCandidate) {
                serviceScope.launch {
                    firebaseClient.sendSignal(
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
                    firebaseClient.sendSignal(
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
                    firebaseClient.sendSignal(
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

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsParser.reset()
        statsJob = serviceScope.launch {
            while (connectionState.value == ConnectionState.CONNECTED) {
                rtcClient?.peerConnection?.getStats { report ->
                    serviceScope.launch(Dispatchers.Default) {
                        val model = statsParser.parse(report)
                        statsFlow.value = model
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
        statsFlow.value = null
    }

    private fun startReconnectionTimer() {
        reconnectionJob?.cancel()
        reconnectionJob = serviceScope.launch {
            Log.d(TAG_WEBRTC, "⏳ [Persistence] -> Disconnected. Waiting 5s for auto-reconnect...")
            connectionState.emit(if (wasEstablished) ConnectionState.RECONNECTING else ConnectionState.CONNECTING)
            delay(5000)
            if (connectionState.value == ConnectionState.RECONNECTING || connectionState.value == ConnectionState.CONNECTING) {
                Log.d(TAG_WEBRTC, "♻️ [Persistence] -> Still disconnected. Triggering ICE Restart.")
                startIceRestart()
            }
        }
    }

    private fun startFallbackTimer() {
        fallbackReconnectionJob?.cancel()
        fallbackReconnectionJob = serviceScope.launch {
            Log.d(TAG_WEBRTC, "⏱️ [Persistence] -> Fallback timer started (15s).")
            delay(25000)
            if (connectionState.value == ConnectionState.CONNECTING ||
                connectionState.value == ConnectionState.RECONNECTING
            ) {
                Log.w(TAG_WEBRTC, "⏰ [Persistence] -> Fallback timeout! Restarting ICE...")
                startIceRestart()
            }
        }
    }

    fun startLocalStream(surface: SurfaceViewRenderer) {
        webRTCFactory.prepareLocalStream(surface)
        rtcClient?.let {
            webRTCFactory.addLocalTracks(it.peerConnection)
        }
    }

    fun initRemoteSurfaceView(remoteSurface: SurfaceViewRenderer) {
        this.remoteSurface = remoteSurface
        webRTCFactory.initSurfaceView(remoteSurface)
        remoteStream?.let {
            it.videoTracks[0]?.addSink(remoteSurface)
        }
    }

    fun switchCamera() = webRTCFactory.switchCamera()

    fun toggleBlur(enabled: Boolean) = webRTCFactory.toggleBlur(enabled)

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
