package com.codewithkael.productionwebrtc.utils.webrt

import org.webrtc.RTCStatsReport

class RTCStatsParser {
    private var lastBytesReceived: Long = 0
    private var lastFramesDecoded: Long = 0
    private var lastTimestampUs: Double = 0.0

    fun reset() {
        lastBytesReceived = 0
        lastFramesDecoded = 0
        lastTimestampUs = 0.0
    }

    fun parse(report: RTCStatsReport): RTCStatsModel {
        var bitrate = 0.0
        var rtt = 0.0
        var jitter = 0.0
        var frameRate = 0

        report.statsMap.values.forEach { stats ->
            when (stats.type) {
                "inbound-rtp" -> {
                    if (stats.members["kind"] == "video") {
                        val bytesReceived = getLong(stats.members["bytesReceived"])
                        val framesDecoded = getLong(stats.members["framesDecoded"])
                        val timestampUs = stats.timestampUs
                        
                        if (lastTimestampUs > 0) {
                            val diffTimeMs = (timestampUs - lastTimestampUs) / 1000.0
                            if (diffTimeMs > 0) {
                                if (lastBytesReceived > 0) {
                                    val diffBytes = bytesReceived - lastBytesReceived
                                    bitrate = (diffBytes * 8.0) / diffTimeMs
                                }
                                if (lastFramesDecoded > 0) {
                                    val diffFrames = framesDecoded - lastFramesDecoded
                                    frameRate = ((diffFrames * 1000.0) / diffTimeMs).toInt()
                                }
                            }
                        }
                        
                        lastBytesReceived = bytesReceived
                        lastFramesDecoded = framesDecoded
                        lastTimestampUs = timestampUs
                        
                        jitter = getDouble(stats.members["jitter"])
                    }
                }
                "remote-inbound-rtp" -> {
                    if (stats.members["kind"] == "video") {
                        if (jitter == 0.0) {
                            jitter = getDouble(stats.members["jitter"])
                        }
                        if (rtt == 0.0) {
                            rtt = getDouble(stats.members["roundTripTime"]) * 1000.0
                        }
                    }
                }
                "candidate-pair" -> {
                    val state = stats.members["state"] as? String
                    if (state == "succeeded" || stats.members["nominated"] == true) {
                        val currentRtt = getDouble(stats.members["currentRoundTripTime"])
                        if (currentRtt > 0) {
                            rtt = currentRtt * 1000.0
                        }
                    }
                }
            }
        }

        return RTCStatsModel(
            bitrate = bitrate,
            rtt = rtt,
            jitter = jitter,
            frameRate = frameRate
        )
    }

    private fun getLong(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun getDouble(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
