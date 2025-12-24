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
    val timingAdvance: Int?
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

/** Общая структура, которую будем отправлять на сервер */
data class MeasurementDto(
    val location: LocationDto,
    val lte: CellInfoLteDto?,
    val gsm: CellInfoGsmDto?,
    val nr: CellInfoNrDto?
)
