
package com.ccm.valecorridapro

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class OverlayController(
    private val context: Context,
    private val onDecision: (Evaluation, RideStatus) -> Unit = { _, _ -> }
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(evaluation: Evaluation) {
        if (view == null) {
            create()
        }

        val root = view ?: return

        val title = root.findViewById<TextView>(R.id.decisionText)
        val offerPrice = root.findViewById<TextView>(R.id.offerPriceText)
        val valuePerKm = root.findViewById<TextView>(R.id.valuePerKmText)
        val totalKm = root.findViewById<TextView>(R.id.totalKmText)
        val passengerRating =
            root.findViewById<TextView>(R.id.passengerRatingText)
        val details = root.findViewById<TextView>(R.id.detailsText)

        val acceptButton =
            root.findViewById<Button>(R.id.acceptRideButton)
        val rejectButton =
            root.findViewById<Button>(R.id.rejectRideButton)
        val ignoreButton =
            root.findViewById<Button>(R.id.ignoreRideButton)


        val (label, backgroundColor) = when (evaluation.decision) {
            Decision.WORTH_IT ->
                "✓ COMPENSA" to Color.rgb(22, 132, 71)

            Decision.REVIEW ->
                "! AVALIAR" to Color.rgb(242, 201, 76)

            Decision.NOT_WORTH_IT ->
                "✕ NÃO COMPENSA" to Color.rgb(201, 54, 54)
        }

        title.text = label
        title.setTextColor(
            if (evaluation.decision == Decision.REVIEW) {
                Color.rgb(35, 35, 35)
            } else {
                Color.WHITE
            }
        )

        val popupBackground =
            root.background.mutate() as? GradientDrawable

        popupBackground?.setColor(
            android.graphics.Color.argb(
                175,
                Color.red(backgroundColor),
                Color.green(backgroundColor),
                Color.blue(backgroundColor)
            )
        )


        offerPrice.text = String.format(
            Locale("pt", "BR"),
            "R$ %.2f",
            evaluation.offer.price
        )

        valuePerKm.text = String.format(
            Locale("pt", "BR"),
            "R$ %.2f",
            evaluation.offer.valuePerKm
        )

        totalKm.text = String.format(
            Locale("pt", "BR"),
            "%.1f km",
            evaluation.offer.totalKm
        )

        passengerRating.text =
            evaluation.offer.passengerRating?.let {
                String.format(Locale("pt", "BR"), "⭐ %.2f", it)
            } ?: "⭐ --"

        details.text = String.format(
            Locale("pt", "BR"),
            "Busca: %.1f km",
            evaluation.offer.pickupKm
        )

        acceptButton.setOnClickListener {
            onDecision(evaluation, RideStatus.ACCEPTED_PENDING)
        }

        rejectButton.setOnClickListener {
            onDecision(evaluation, RideStatus.REJECTED)
        }

        ignoreButton.setOnClickListener {
            onDecision(evaluation, RideStatus.IGNORED)
        }

        root.visibility = View.VISIBLE
    }

    private fun create() {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.overlay_result, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        // Recolher e expandir o popup.
        val toggle = root.findViewById<TextView>(R.id.overlayToggle)

        val collapsibleViews = listOf(
            root.findViewById<View>(R.id.decisionText),
            root.findViewById<View>(R.id.offerPriceText).parent as View,
            root.findViewById<View>(R.id.totalKmText).parent.parent as View,
            root.findViewById<View>(R.id.detailsText),
            root.findViewById<View>(R.id.overlayActions)
        )

        var collapsed = false

        toggle.setOnClickListener {
            collapsed = !collapsed

            collapsibleViews.forEach { item ->
                item.visibility =
                    if (collapsed) View.GONE else View.VISIBLE
            }

            toggle.text = if (collapsed) "+" else "−"

            root.requestLayout()
            params?.let {
                windowManager.updateViewLayout(root, it)
            }
        }

        // Arrastar pelo cabeçalho.
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0

        val header = root.findViewById<View>(R.id.overlayHeader)

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    startX = event.rawX
                    startY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x =
                        initialX + (event.rawX - startX).toInt()

                    layoutParams.y =
                        initialY + (event.rawY - startY).toInt()

                    windowManager.updateViewLayout(root, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - startX) > 8 ||
                            kotlin.math.abs(event.rawY - startY) > 8
                    moved
                }

                else -> false
            }
        }

        windowManager.addView(root, layoutParams)

        view = root
        params = layoutParams
    }

    fun remove() {
        view?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        view = null
        params = null
    }
}