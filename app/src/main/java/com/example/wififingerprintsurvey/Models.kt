package com.example.wififingerprintsurvey

data class WifiMeasurement(
    val bssid: String,
    val rssi: Int,
    val ssid: String
)

data class SurveyRecord(
    val id: Long = 0,
    val label: String,
    val timestamp: Long,
    val measurements: List<WifiMeasurement>,
    val coordX: Float? = null,
    val coordY: Float? = null,
    val floorPlanName: String? = null
)
