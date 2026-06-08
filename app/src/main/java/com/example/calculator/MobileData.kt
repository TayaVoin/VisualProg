package com.example.calculator

data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,
    val speed: Float,
    val accuracy: Float
)

data class CellInfoLteDto(
    val band: Int?,
    val earfcn: Int?,
    val mcc: Int?,
    val mnc: Int?,
    val pci: Int?,
    val tac: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val cqi: Int?,
    val timingAdvance: Int?,
    val rssi: Int? = null,
    val registered: Boolean? = null
)

data class CellInfoGsmDto(
    val cid: Int?,
    val lac: Int?,
    val mcc: Int?,
    val mnc: Int?,
    val bsic: Int?,
    val arfcn: Int?,
    val rssi: Int?,
    val timingAdvance: Int?
)

data class CellInfoNrDto(
    val nci: Long?,
    val nrarfcn: Int?,
    val tac: Int?,
    val mcc: Int?,
    val mnc: Int?,
    val ssRsrp: Int?,
    val ssRsrq: Int?,
    val ssSinr: Int?
)

data class AppTraffic(
    val packageName: String,
    val appName: String,
    val rxBytes: Long,
    val txBytes: Long
)

data class TrafficInfo(
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val topApps: List<AppTraffic>
)

data class MeasurementDto(
    val location: LocationDto,
    val lteCells: List<CellInfoLteDto>,
    val gsmCells: List<CellInfoGsmDto>,
    val nrCells: List<CellInfoNrDto>,
    val traffic: TrafficInfo
)