package com.example.calculator

import android.content.Context
import android.net.TrafficStats

class TrafficCollector(private val context: Context) {
    fun getTrafficInfo(): TrafficInfo {
        val totalRx = TrafficStats.getTotalRxBytes() ?: 0L
        val totalTx = TrafficStats.getTotalTxBytes() ?: 0L
        return TrafficInfo(totalRx, totalTx, emptyList())
    }
}