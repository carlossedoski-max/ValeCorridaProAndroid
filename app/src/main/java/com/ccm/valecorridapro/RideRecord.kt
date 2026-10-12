package com.ccm.valecorridapro

import java.util.UUID

enum class RideStatus {
    ACCEPTED_PENDING,
    COMPLETED,
    REJECTED,
    IGNORED
}

data class RideRecord(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val sourceApp: String = "UNKNOWN",

    val offerPrice: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val estimatedMinutes: Int,
    val passengerRating: Double? = null,

    val status: RideStatus = RideStatus.ACCEPTED_PENDING,

    val actualFare: Double? = null,
    val actualKm: Double? = null,
    val completedAt: Long? = null,

    val sourceText: String = ""
) {
    val estimatedTotalKm: Double
        get() = pickupKm + tripKm

    val operatingCost: Double
        get() = (actualKm ?: 0.0) * 0.50

    val estimatedProfit: Double
        get() = (actualFare ?: 0.0) - operatingCost
}
