package com.codewithkael.productionwebrtc.utils.webrt

import android.util.Log
import com.codewithkael.productionwebrtc.utils.Tags
import org.webrtc.*

open class MyPeerObserver : PeerConnection.Observer {
    private val TAG = Tags.peerObserverTag

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        Log.d(TAG, "🔄 [Signaling State] -> $state")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Log.d(TAG, "🔌 [ICE Connection State] -> $state")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d(TAG, "📡 [ICE Receiving] -> $receiving")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "⛏️ [ICE Gathering State] -> $state")
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        Log.d(TAG, "❄️ [Local ICE Candidate Generated] -> ${candidate?.sdpMid}")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Log.d(TAG, "🧹 [ICE Candidates Removed] -> count: ${candidates?.size}")
    }

    override fun onAddStream(stream: MediaStream?) {
        Log.d(TAG, "🎥 [Remote Stream Added] -> ID: ${stream?.id}")
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Log.d(TAG, "🗑️ [Remote Stream Removed] -> ID: ${stream?.id}")
    }

    override fun onDataChannel(channel: DataChannel?) {
        Log.d(TAG, "📦 [Data Channel Created] -> label: ${channel?.label()}")
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "♻️ [Renegotiation Needed]")
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        Log.d(TAG, "🎵 [Remote Track Added] -> ID: ${receiver?.id()}, Streams: ${streams?.size}")
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        val symbol = when(newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> "✅"
            PeerConnection.PeerConnectionState.FAILED -> "❌"
            PeerConnection.PeerConnectionState.DISCONNECTED -> "🔌"
            else -> "🔄"
        }
        Log.d(TAG, "$symbol [Peer Connection State] -> $newState")
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        Log.d(TAG, "🎞️ [Track Received] -> ID: ${transceiver?.receiver?.id()}")
    }
}
