package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_records")
data class DiagnosticRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val recordType: String, // "DIAGNOSTIC", "AUTO_FIX", "SPEED_TEST"
    val networkName: String,
    val gatewayIp: String,
    val healthScore: Int,
    val gatewayPingMs: Int,
    val internetPingMs: Int,
    val jitterMs: Int,
    val packetLossPercent: Int,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val issuesFound: Int,
    val issuesFixed: Int,
    val recommendedDns: String,
    val summaryText: String
)
