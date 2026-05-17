package com.codewithkael.productionwebrtc.utils.webrt

import org.webrtc.RTCStatsReport

class RTCStatsParser {
    private var lastBytesSent: Long = 0
    private var lastTimestampUs: Double = 0.0

    fun parse(report: RTCStatsReport): RTCStatsModel {
        var bitrate = 0.0
        var packetLoss = 0L
        var rtt = 0.0
        var jitter = 0.0
        var frameRate = 0

        report.statsMap.values.forEach { stats ->
            when (stats.type) {
                "outbound-rtp" -> {
                    if (stats.members["kind"] == "video") {
                        val bytesSent = stats.members["bytesSent"] as? Long ?: 0L
                        val timestampUs = stats.timestampUs
                        
                        if (lastBytesSent > 0 && lastTimestampUs > 0) {
                            val diffBytes = bytesSent - lastBytesSent
                            val diffTimeMs = (timestampUs - lastTimestampUs) / 1000.0
                            if (diffTimeMs > 0) {
                                // (Bytes * 8 bits) / (time in ms / 1000) = bits per second
                                // (Bytes * 8) / time in ms = kbps
                                bitrate = (diffBytes * 8.0) / diffTimeMs
                            }
                        }
                        lastBytesSent = bytesSent
                        lastTimestampUs = timestampUs
                        
                        frameRate = (stats.members["framesPerSecond"] as? Double ?: 0.0).toInt()
                        packetLoss = stats.members["packetsLost"] as? Long ?: 0L
                    }
                }
                "remote-inbound-rtp" -> {
                    if (stats.members["kind"] == "video") {
                        rtt = (stats.members["roundTripTime"] as? Double ?: 0.0) * 1000.0 // to ms
                        jitter = stats.members["jitter"] as? Double ?: 0.0
                    }
                }
            }
        }

        return RTCStatsModel(
            bitrate = bitrate,
            packetLoss = packetLoss,
            rtt = rtt,
            jitter = jitter,
            frameRate = frameRate
        )
    }
}
