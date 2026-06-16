package com.codewithkael.productionwebrtc.utils.webrt

data class RTCStatsModel(
    val bitrate: Double = 0.0, // in kbps
    val rtt: Double = 0.0, // in ms
    val jitter: Double = 0.0, // in seconds
    val frameRate: Int = 0
)
