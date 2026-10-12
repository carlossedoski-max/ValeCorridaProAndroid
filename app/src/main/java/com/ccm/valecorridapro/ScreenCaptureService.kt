package com.ccm.valecorridapro

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    private val processing = AtomicBoolean(false)

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var overlay: OverlayController

    private var lastSignature = ""
    private var lastProcessedAt = 0L

    // Remove o popup automaticamente após 5 segundos
    private val hideOverlayRunnable = Runnable {
        overlay.remove()
    }


    override fun onCreate() {
        super.onCreate()

        overlay = OverlayController(this) { evaluation, status ->

            val ride = RideRecord(
                sourceApp = "UNKNOWN",
                offerPrice = evaluation.offer.price,
                pickupKm = evaluation.offer.pickupKm,
                tripKm = evaluation.offer.tripKm,
                estimatedMinutes = evaluation.offer.totalMinutes,
                passengerRating = evaluation.offer.passengerRating,
                status = status,
                sourceText = evaluation.offer.sourceText
            )

            RideStorage(this).save(ride)

            mainHandler.removeCallbacks(hideOverlayRunnable)
            overlay.remove()
        }

        createNotificationChannel()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopCapture()
            }

            ACTION_START -> {

                startForegroundSafely()

                val code = intent.getIntExtra(
                    EXTRA_RESULT_CODE,
                    Activity.RESULT_CANCELED
                )

                val data =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            EXTRA_RESULT_DATA,
                            Intent::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }

                if (
                    code == Activity.RESULT_OK &&
                    data != null
                ) {
                    startCapture(code, data)
                } else {
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {

        val stopIntent =
            Intent(this, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }

        val stopPending =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or
                        PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Vale Corrida Pro ativo")
                .setContentText(
                    "Analisando ofertas exibidas na tela"
                )
                .setOngoing(true)
                .addAction(
                    0,
                    "Parar",
                    stopPending
                )
                .build()

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun startCapture(
        resultCode: Int,
        data: Intent
    ) {

        if (projection != null) return

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        projection =
            manager.getMediaProjection(
                resultCode,
                data
            )

        projection?.registerCallback(
            object : MediaProjection.Callback() {

                override fun onStop() {
                    stopCapture()
                }

            },
            Handler(mainLooper)
        )

        val wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        imageReader =
            ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            )

        workerThread =
            HandlerThread("ride-ocr").also {
                it.start()
            }

        worker =
            Handler(workerThread!!.looper)

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatest(reader)
            },
            worker
        )

        virtualDisplay =
            projection?.createVirtualDisplay(
                "ValeCorridaCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                worker
            )
    }

    private fun processLatest(
        reader: ImageReader
    ) {

        val now = System.currentTimeMillis()

        val image =
            reader.acquireLatestImage()
                ?: return

        if (
            processing.get() ||
            now - lastProcessedAt < 700
        ) {
            image.close()
            return
        }

        processing.set(true)
        lastProcessedAt = now

        val bitmap = image.toBitmap()

        image.close()

        recognizer.process(
            InputImage.fromBitmap(bitmap, 0)
        )
            .addOnSuccessListener { result ->

                val offer =
                    RideOfferParser.parse(result.text)

                if (offer != null) {

                    val signature =
                        "${offer.price}-${offer.pickupKm}-${offer.tripKm}-${offer.totalMinutes}"

                    // Só mostra novamente quando aparecer
                    // uma oferta diferente
                    if (signature != lastSignature) {

                        lastSignature = signature

                        val prefs =
                            getSharedPreferences(
                                "ride_rules",
                                Context.MODE_PRIVATE
                            )

                        val evaluation =
                            RideEvaluator.evaluate(
                                offer,
                                prefs.getFloat(
                                    "min_per_km",
                                    1.80f
                                ).toDouble(),
                                prefs.getFloat(
                                    "min_per_hour",
                                    35f
                                ).toDouble(),
                                prefs.getFloat(
                                    "max_pickup_km",
                                    5f
                                ).toDouble()
                            )

                        mainHandler.post {

                            // Cancela qualquer remoção anterior
                            mainHandler.removeCallbacks(
                                hideOverlayRunnable
                            )

                            // Mostra a nova avaliação
                            overlay.show(evaluation)

                            // Remove após 5 segundos
                            mainHandler.postDelayed(
                                hideOverlayRunnable,
                                8_000L
                            )
                        }
                    }
                }
            }
            .addOnCompleteListener {

                bitmap.recycle()

                processing.set(false)
            }
    }

    private fun Image.toBitmap(): Bitmap {

        val plane = planes[0]

        val buffer = plane.buffer

        val pixelStride = plane.pixelStride

        val rowPadding =
            plane.rowStride -
                    pixelStride * width

        val padded =
            Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )

        padded.copyPixelsFromBuffer(buffer)

        val cropped =
            Bitmap.createBitmap(
                padded,
                0,
                0,
                width,
                height
            )

        if (cropped !== padded) {
            padded.recycle()
        }

        return cropped
    }

    private fun stopCapture() {

        imageReader?.setOnImageAvailableListener(
            null,
            null
        )

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        val activeProjection = projection

        projection = null

        activeProjection?.stop()

        workerThread?.quitSafely()

        workerThread = null
        worker = null

        // Cancela o timer do popup
        mainHandler.removeCallbacks(
            hideOverlayRunnable
        )

        // Remove o popup
        overlay.remove()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        mainHandler.removeCallbacks(
            hideOverlayRunnable
        )

        overlay.remove()

        recognizer.close()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Monitoramento de corridas",
                    NotificationManager.IMPORTANCE_LOW
                )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    companion object {

        const val ACTION_START =
            "com.ccm.valecorridapro.START"

        const val ACTION_STOP =
            "com.ccm.valecorridapro.STOP"

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_RESULT_DATA =
            "result_data"

        private const val CHANNEL_ID =
            "ride_monitor"

        private const val NOTIFICATION_ID =
            4107
    }
}