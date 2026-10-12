
package com.ccm.valecorridapro

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MyRidesActivity : AppCompatActivity() {

    private lateinit var storage: RideStorage
    private lateinit var summaryText: TextView
    private lateinit var pendingContainer: LinearLayout
    private lateinit var emptyText: TextView

    private var monthlyResetButton: Button? = null

    private val monthlyPrefs by lazy {
        getSharedPreferences(
            "vale_corrida_monthly_summary",
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_rides)

        storage = RideStorage(this)
        summaryText = findViewById(R.id.summaryText)
        pendingContainer = findViewById(R.id.pendingRidesContainer)
        emptyText = findViewById(R.id.emptyText)

        setupMonthlyResetButton()
        renderRides()
    }

    override fun onResume() {
        super.onResume()
        if (::storage.isInitialized) {
            renderRides()
        }
    }

    private fun monthStartMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun setupMonthlyResetButton() {
        if (monthlyResetButton != null) return

        val parent = summaryText.parent as? LinearLayout ?: return

        val button = Button(this).apply {
            id = View.generateViewId()
            text = "Zerar resumo do mês"

            setOnClickListener {
                AlertDialog.Builder(this@MyRidesActivity)
                    .setTitle("Zerar resumo mensal?")
                    .setMessage(
                        "O resumo começará a contar a partir de agora. " +
                                "As corridas antigas continuarão salvas."
                    )
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Zerar resumo") { _, _ ->
                        monthlyPrefs.edit()
                            .putLong(
                                "reset_timestamp",
                                System.currentTimeMillis()
                            )
                            .apply()

                        renderRides()

                        Toast.makeText(
                            this@MyRidesActivity,
                            "Resumo mensal reiniciado.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
            }
        }

        val summaryIndex = parent.indexOfChild(summaryText)
        parent.addView(button, summaryIndex + 1)
        monthlyResetButton = button
    }

    private fun renderRides() {
        val rides = storage.getAll()

        val pending = rides.filter {
            it.status == RideStatus.ACCEPTED_PENDING
        }

        val completed = rides.filter {
            it.status == RideStatus.COMPLETED
        }

        val monthStart = monthStartMillis()
        val resetTimestamp = monthlyPrefs.getLong(
            "reset_timestamp",
            monthStart
        )
        val effectiveStart = maxOf(monthStart, resetTimestamp)

        val monthlyCompleted = completed.filter { ride ->
            val completedAt = ride.completedAt ?: ride.createdAt
            completedAt >= effectiveStart
        }

        val revenue = monthlyCompleted.sumOf {
            it.actualFare ?: 0.0
        }
        val cost = monthlyCompleted.sumOf {
            it.operatingCost
        }
        val profit = revenue - cost

        summaryText.text = String.format(
            Locale("pt", "BR"),
            "RESUMO DO MÊS\n" +
                    "Corridas concluídas: %d\n" +
                    "Receita: R$ %.2f\n" +
                    "Custo estimado: R$ %.2f\n" +
                    "Lucro estimado: R$ %.2f",
            monthlyCompleted.size,
            revenue,
            cost,
            profit
        )

        pendingContainer.removeAllViews()

        emptyText.visibility =
            if (pending.isEmpty()) View.VISIBLE else View.GONE

        pending.forEach { ride ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(android.graphics.Color.WHITE)
            }

            val info = TextView(this).apply {
                text = String.format(
                    Locale("pt", "BR"),
                    "Oferta: R$ %.2f\nBusca: %.1f km\n" +
                            "Trajeto estimado: %.1f km\n" +
                            "Tempo estimado: %d min",
                    ride.offerPrice,
                    ride.pickupKm,
                    ride.tripKm,
                    ride.estimatedMinutes
                )
                textSize = 16f
            }

            val completeButton = Button(this).apply {
                text = "Concluir corrida"
                setOnClickListener {
                    showCompleteDialog(ride)
                }
            }

            card.addView(info)
            card.addView(completeButton)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }

            pendingContainer.addView(card, params)
        }
    }

    private fun showCompleteDialog(ride: RideRecord) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }

        val fareInput = EditText(this).apply {
            hint = "Valor recebido (R$)"
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val kmInput = EditText(this).apply {
            hint = "Quilômetros realmente rodados"
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        container.addView(fareInput)
        container.addView(kmInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Concluir corrida")
            .setMessage("Informe os valores reais da corrida.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val fare = parseNumber(fareInput.text.toString())
                    val km = parseNumber(kmInput.text.toString())

                    if (fare == null || fare < 0 ||
                        km == null || km <= 0
                    ) {
                        Toast.makeText(
                            this,
                            "Informe um valor válido e quilômetros maiores que zero.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    val saved = storage.completeRide(
                        ride.id,
                        fare,
                        km
                    )

                    if (saved) {
                        val profit = fare - (km * 0.50)

                        Toast.makeText(
                            this,
                            String.format(
                                Locale("pt", "BR"),
                                "Corrida concluída! Lucro estimado: R$ %.2f",
                                profit
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        dialog.dismiss()
                        renderRides()
                    } else {
                        Toast.makeText(
                            this,
                            "Não foi possível concluir. Atualize a lista e tente novamente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        dialog.show()
    }

    private fun parseNumber(value: String): Double? {
        return value.trim()
            .replace(",", ".")
            .toDoubleOrNull()
    }
}
