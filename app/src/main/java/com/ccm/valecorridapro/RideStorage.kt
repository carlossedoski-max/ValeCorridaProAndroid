package com.ccm.valecorridapro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RideStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "vale_corrida_rides",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getAll(): List<RideRecord> {
        val json = prefs.getString("rides", "[]") ?: "[]"
        val array = JSONArray(json)
        val rides = mutableListOf<RideRecord>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            rides.add(
                RideRecord(
                    id = item.getString("id"),
                    createdAt = item.getLong("createdAt"),
                    sourceApp = item.optString("sourceApp", "UNKNOWN"),
                    offerPrice = item.getDouble("offerPrice"),
                    pickupKm = item.getDouble("pickupKm"),
                    tripKm = item.getDouble("tripKm"),
                    estimatedMinutes = item.getInt("estimatedMinutes"),
                    passengerRating = if (item.isNull("passengerRating")) {
                        null
                    } else {
                        item.optDouble("passengerRating")
                    },
                    status = runCatching {
                        RideStatus.valueOf(item.getString("status"))
                    }.getOrDefault(RideStatus.ACCEPTED_PENDING),
                    actualFare = if (item.isNull("actualFare")) {
                        null
                    } else {
                        item.optDouble("actualFare")
                    },
                    actualKm = if (item.isNull("actualKm")) {
                        null
                    } else {
                        item.optDouble("actualKm")
                    },
                    completedAt = if (item.isNull("completedAt")) {
                        null
                    } else {
                        item.optLong("completedAt")
                    },
                    sourceText = item.optString("sourceText", "")
                )
            )
        }

        return rides
    }

    @Synchronized
    fun save(ride: RideRecord) {
        val rides = getAll().toMutableList()
        val index = rides.indexOfFirst { it.id == ride.id }

        if (index >= 0) {
            rides[index] = ride
        } else {
            rides.add(ride)
        }

        saveAll(rides)
    }

    @Synchronized
    fun update(ride: RideRecord) {
        save(ride)
    }

    @Synchronized
    fun completeRide(
        id: String,
        actualFare: Double,
        actualKm: Double
    ): Boolean {
        if (actualFare < 0 || actualKm <= 0) {
            return false
        }

        val ride = getAll().firstOrNull { it.id == id }
            ?: return false

        if (ride.status != RideStatus.ACCEPTED_PENDING) {
            return false
        }

        val completedRide = ride.copy(
            status = RideStatus.COMPLETED,
            actualFare = actualFare,
            actualKm = actualKm,
            completedAt = System.currentTimeMillis()
        )

        save(completedRide)
        return true
    }


    @Synchronized
    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    private fun saveAll(rides: List<RideRecord>) {
        val array = JSONArray()

        rides.forEach { ride ->
            val item = JSONObject()

            item.put("id", ride.id)
            item.put("createdAt", ride.createdAt)
            item.put("sourceApp", ride.sourceApp)
            item.put("offerPrice", ride.offerPrice)
            item.put("pickupKm", ride.pickupKm)
            item.put("tripKm", ride.tripKm)
            item.put("estimatedMinutes", ride.estimatedMinutes)

            item.put(
                "passengerRating",
                ride.passengerRating ?: JSONObject.NULL
            )

            item.put("status", ride.status.name)
            item.put("actualFare", ride.actualFare ?: JSONObject.NULL)
            item.put("actualKm", ride.actualKm ?: JSONObject.NULL)
            item.put("completedAt", ride.completedAt ?: JSONObject.NULL)
            item.put("sourceText", ride.sourceText)

            array.put(item)
        }

        prefs.edit()
            .putString("rides", array.toString())
            .apply()
    }
}
