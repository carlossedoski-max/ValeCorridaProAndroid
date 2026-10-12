package com.ccm.valecorridapro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ccm.valecorridapro.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("ride_rules", MODE_PRIVATE) }

    private val overlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture()
            } else {
                Toast.makeText(
                    this,
                    "A sobreposição é necessária para exibir o resultado.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val screenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == Activity.RESULT_OK && result.data != null) {

                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(
                        ScreenCaptureService.EXTRA_RESULT_CODE,
                        result.resultCode
                    )
                    putExtra(
                        ScreenCaptureService.EXTRA_RESULT_DATA,
                        result.data
                    )
                }

                ContextCompat.startForegroundService(this, intent)

                binding.statusText.text =
                    "Monitoramento ativo — abra Uber ou 99"

            } else {

                binding.statusText.text =
                    "Captura não autorizada"
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadRules()

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        binding.saveButton.setOnClickListener {
            saveRules()
        }

        binding.startButton.setOnClickListener {

            saveRules(showConfirmation = false)

            if (!Settings.canDrawOverlays(this)) {

                overlayPermission.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )

            } else {

                requestScreenCapture()
            }
        }

        binding.stopButton.setOnClickListener {

            startService(
                Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_STOP
                }
            )

            binding.statusText.text =
                "Monitoramento desligado"
        }


        binding.myRidesButton.setOnClickListener {
            startActivity(
                Intent(this, MyRidesActivity::class.java)
            )
        }

    }




    private fun requestScreenCapture() {

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        screenCapture.launch(
            manager.createScreenCaptureIntent()
        )
    }

    private fun loadRules() {

        binding.minPerKm.setText(
            format(
                prefs.getFloat(
                    "min_per_km",
                    1.80f
                )
            )
        )

        binding.minPerHour.setText(
            format(
                prefs.getFloat(
                    "min_per_hour",
                    35f
                )
            )
        )

        binding.maxPickupKm.setText(
            format(
                prefs.getFloat(
                    "max_pickup_km",
                    5f
                )
            )
        )

        // NOVO: nota mínima do passageiro
        binding.minPassengerRating.setText(
            format(
                prefs.getFloat(
                    "min_passenger_rating",
                    4.80f
                )
            )
        )
    }

    private fun saveRules(
        showConfirmation: Boolean = true
    ) {

        val km =
            parse(
                binding.minPerKm.text?.toString(),
                1.80f
            )

        val hour =
            parse(
                binding.minPerHour.text?.toString(),
                35f
            )

        val pickup =
            parse(
                binding.maxPickupKm.text?.toString(),
                5f
            )

        val passengerRating =
            parse(
                binding.minPassengerRating.text?.toString(),
                4.80f
            )

        prefs.edit()
            .putFloat("min_per_km", km)
            .putFloat("min_per_hour", hour)
            .putFloat("max_pickup_km", pickup)
            .putFloat("min_passenger_rating", passengerRating)
            .apply()

        if (showConfirmation) {

            Toast.makeText(
                this,
                "Critérios salvos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun parse(
        value: String?,
        fallback: Float
    ): Float {

        return value
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?: fallback
    }

    private fun format(
        value: Float
    ): String {

        return String.format(
            Locale.US,
            "%.2f",
            value
        ).replace('.', ',')
    }
}