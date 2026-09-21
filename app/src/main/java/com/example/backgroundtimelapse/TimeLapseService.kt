package com.example.backgroundtimelapse

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TimeLapseService : LifecycleService() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private var frameCount = 0
    private val intervalMs = 1000L // 1 frame per second
    private val outputDir by lazy { File(getExternalFilesDir(null), "frames").apply { mkdirs() } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(1, createNotification())
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeLapse::WakeLock")
        wakeLock.acquire(25 * 60 * 60 * 1000L) 

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupCamera()
        startCaptureLoop()
        return START_STICKY
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
            } catch (exc: Exception) {
                Log.e("TimeLapse", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCaptureLoop() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                takePictureWithTimestamp()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(runnable)
    }

    private fun takePictureWithTimestamp() {
        val file = File(outputDir, "frame_${frameCount++}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                addTimestampToImage(file)
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("TimeLapse", "Capture failed: ${exception.message}")
            }
        })
    }

    private fun addTimestampToImage(file: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                textSize = 60f
                isAntiAlias = true
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            canvas.drawText(sdf.format(Date()), 50f, 100f, paint)
            
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            mutableBitmap.recycle()
        } catch (e: Exception) {
            Log.e("TimeLapse", "Error adding timestamp", e)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "timelapse_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TimeLapse Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("24hr TimeLapse Running")
            .setContentText("Recording frames in background...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        // Photos are saved in: Android/data/com.example.backgroundtimelapse/files/frames
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
