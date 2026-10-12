package com.ccm.valecorridapro

object RideEvaluator {

    fun evaluate(
        offer: RideOffer,
        minPerKm: Double,
        minPerHour: Double,
        maxPickupKm: Double,
        minPassengerRating: Double = 4.80
    ): Evaluation {

        val kmOk = offer.valuePerKm >= minPerKm

        val hourOk = offer.valuePerHour >= minPerHour

        val pickupOk = offer.pickupKm <= maxPickupKm

        // Verifica a nota do passageiro
        val ratingOk = offer.passengerRating?.let {
            it >= minPassengerRating
        }

        return when {

            // Todos os critérios, incluindo nota, estão bons
            kmOk &&
                    hourOk &&
                    pickupOk &&
                    ratingOk == true ->

                Evaluation(
                    Decision.WORTH_IT,
                    offer,
                    "Todos os critérios atingidos"
                )

            // Nota abaixo do mínimo
            ratingOk == false ->

                Evaluation(
                    Decision.NOT_WORTH_IT,
                    offer,
                    "Nota do passageiro abaixo do mínimo"
                )

            // Nota não identificada
            ratingOk == null ->

                Evaluation(
                    Decision.REVIEW,
                    offer,
                    "Nota do passageiro não identificada"
                )

            // Distância de busca ruim ou economia muito ruim
            !pickupOk ||
                    (!kmOk && !hourOk) ->

                Evaluation(
                    Decision.NOT_WORTH_IT,
                    offer,
                    "Fora dos critérios principais"
                )

            // Demais situações
            else ->

                Evaluation(
                    Decision.REVIEW,
                    offer,
                    "Um dos critérios ficou abaixo"
                )
        }
    }
}