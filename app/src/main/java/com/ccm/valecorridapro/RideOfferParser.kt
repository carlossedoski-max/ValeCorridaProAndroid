package com.ccm.valecorridapro

object RideOfferParser {

    private val moneyRegex =
        Regex(
            "R\\$\\s*([0-9]{1,3}(?:[.,][0-9]{2})?)",
            RegexOption.IGNORE_CASE
        )

    private val kmRegex =
        Regex(
            "([0-9]+(?:[.,][0-9]+)?)\\s*km",
            RegexOption.IGNORE_CASE
        )

    private val minRegex =
        Regex(
            "([0-9]{1,3})\\s*min",
            RegexOption.IGNORE_CASE
        )

    // Procura possíveis notas de passageiro:
    // 4,92 / 4.92 / 5,00 / 5.0 etc.
    private val ratingRegex =
        Regex(
            "(?<![0-9])([4-5](?:[.,][0-9]{1,2})?)(?![0-9])"
        )

    fun parse(raw: String): RideOffer? {

        val normalized =
            raw
                .replace('\u00A0', ' ')
                .replace("  ", " ")

        val price =
            moneyRegex
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.decimal()
                ?: return null

        val distances =
            kmRegex
                .findAll(normalized)
                .mapNotNull {
                    it.groupValues
                        .getOrNull(1)
                        ?.decimal()
                }
                .toList()

        val minutes =
            minRegex
                .findAll(normalized)
                .mapNotNull {
                    it.groupValues
                        .getOrNull(1)
                        ?.toIntOrNull()
                }
                .toList()

        if (
            distances.isEmpty() ||
            minutes.isEmpty()
        ) {
            return null
        }

        val pickup =
            when {
                distances.size >= 2 ->
                    distances.first()

                else ->
                    0.0
            }

        val trip =
            when {
                distances.size >= 2 ->
                    distances[1]

                else ->
                    distances.first()
            }

        // Procura uma nota entre 4.0 e 5.0
        val passengerRating =
            ratingRegex
                .findAll(normalized)
                .mapNotNull {
                    it.groupValues
                        .getOrNull(1)
                        ?.decimal()
                }
                .firstOrNull {
                    it in 4.0..5.0
                }

        return RideOffer(

            price = price,

            pickupKm = pickup,

            tripKm = trip,

            totalMinutes =
                minutes
                    .take(2)
                    .sum(),

            passengerRating =
                passengerRating,

            sourceText = normalized
        )
    }

    private fun String.decimal(): Double? {

        val cleaned =
            trim()

        return when {

            cleaned.contains(',') &&
                    cleaned.contains('.') ->

                cleaned
                    .replace(".", "")
                    .replace(',', '.')
                    .toDoubleOrNull()

            cleaned.contains(',') ->

                cleaned
                    .replace(',', '.')
                    .toDoubleOrNull()

            else ->

                cleaned.toDoubleOrNull()
        }
    }
}