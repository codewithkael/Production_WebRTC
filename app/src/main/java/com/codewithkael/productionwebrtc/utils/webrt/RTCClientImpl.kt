package com.codewithkael.productionwebrtc.utils.webrt

import android.util.Log
import com.codewithkael.productionwebrtc.utils.Tags
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class RTCClientImpl(
    connection: PeerConnection,
    private val transferListener: TransferDataToServerCallback
) : RTCClient {

    private val TAG = Tags.rtcClientTag

    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    override val peerConnection: PeerConnection = connection

    override fun offer() {
        Log.d(TAG, "🚀 [Action] -> Creating Offer...")
        peerConnection.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                Log.d(TAG, "💾 [Action] -> Setting Local Description (OFFER)...")
                peerConnection.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        Log.d(TAG, "🏁 [Action] -> Local Description Set (OFFER)")
                    }
                }, desc)
                desc?.let {
                    transferListener.onOfferGenerated(desc)
                }
            }
        }, mediaConstraint)
    }

    override fun answer() {
        Log.d(TAG, "🚀 [Action] -> Creating Answer...")
        peerConnection.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                Log.d(TAG, "💾 [Action] -> Setting Local Description (ANSWER)...")
                peerConnection.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        Log.d(TAG, "🏁 [Action] -> Local Description Set (ANSWER)")
                        desc?.let { transferListener.onAnswerGenerated(it) }
                    }
                }, desc)
            }
        }, mediaConstraint)
    }


    override fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        Log.d(TAG, "📥 [Action] -> Setting Remote Description (${sessionDescription.type})...")
        peerConnection.setRemoteDescription(object : MySdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "🏁 [Action] -> Remote Description Set Successfully")
            }
        }, sessionDescription)
    }

    override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        Log.d(TAG, "❄️ [Action] -> Adding Remote ICE Candidate")
        peerConnection.addIceCandidate(iceCandidate)
    }


    override fun onDestroy() {
        Log.d(TAG, "🧹 [Action] -> Destroying RTCClient")
        runCatching {
            peerConnection.close()
        }
    }


    override fun onLocalIceCandidateGenerated(iceCandidate: IceCandidate) {
        Log.d(TAG, "❄️ [Action] -> Adding Local ICE Candidate & Notifying Server")
        peerConnection.addIceCandidate(iceCandidate)
        transferListener.onIceGenerated(iceCandidate)
    }

    interface TransferDataToServerCallback {
        fun onIceGenerated(iceCandidate: IceCandidate)
        fun onOfferGenerated(sessionDescription: SessionDescription)
        fun onAnswerGenerated(sessionDescription: SessionDescription)
    }
}
