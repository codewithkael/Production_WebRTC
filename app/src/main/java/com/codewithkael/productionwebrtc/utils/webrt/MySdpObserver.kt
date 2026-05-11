package com.codewithkael.productionwebrtc.utils.webrt

import android.util.Log
import com.codewithkael.productionwebrtc.utils.Tags
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class MySdpObserver : SdpObserver {
    private val TAG = Tags.sdpObserverTag

    override fun onCreateSuccess(desc: SessionDescription?) {
        Log.d(TAG, "📝 [SDP Create Success] -> Type: ${desc?.type}")
    }

    override fun onSetSuccess() {
        Log.d(TAG, "✅ [SDP Set Success]")
    }

    override fun onCreateFailure(error: String?) {
        Log.e(TAG, "❌ [SDP Create Failure] -> $error")
    }

    override fun onSetFailure(error: String?) {
        Log.e(TAG, "❌ [SDP Set Failure] -> $error")
    }
}
