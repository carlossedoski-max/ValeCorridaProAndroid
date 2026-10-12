package com.ccm.valecorridapro

data class RideOffer(
    val price: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val totalMinutes: Int,
    val passengerRating: Double? = null,
    val sourceText: String
) {
    val totalKm: Double
        get() = pickupKm + tripKm

    val valuePerKm: Double
        get() = if (totalKm > 0) price / totalKm else 0.0

    val valuePerHour: Double
        get() = if (totalMinutes > 0)
            price / (totalMinutes / 60.0)
        else 0.0
}

enum class Decision {
    WORTH_IT,
    REVIEW,
    NOT_WORTH_IT
}

data class Evaluation(
    val decision: Decision,
    val offer: RideOffer,
    val reason: String
)