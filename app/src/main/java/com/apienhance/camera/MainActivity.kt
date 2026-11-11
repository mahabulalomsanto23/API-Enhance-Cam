package com.apienhance.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setContent { CameraScreen() }
        else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setContent { CameraScreen() }
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    @Composable
    private fun CameraScreen() {
        val context = LocalContext.current
        var message by remember { mutableStateOf("Ready — original photos save immediately") }
        var busy by remember { mutableStateOf(false) }

        MaterialTheme {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("API Enhance Camera", style = MaterialTheme.typography.headlineSmall)
                Text("Original is saved first. AI enhancement runs in the background.")
                Spacer(Modifier.height(12.dp))
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                val captureUseCase = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                imageCapture = captureUseCase
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(this@MainActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, captureUseCase)
                                    message = "Ready — original photos save immediately"
                                } catch (e: Exception) {
                                    message = "Camera error: ${e.localizedMessage}"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !busy && imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        busy = true
                        message = "Saving original photo…"
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "Original_$stamp.jpg")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/API Enhance Camera/Original")
                        }
                        val output = ImageCapture.OutputFileOptions.Builder(
                            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        ).build()
                        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                val uri = result.savedUri
                                runOnUiThread {
                                    busy = false
                                    if (uri == null) {
                                        message = "Original saved, but its URI was unavailable."
                                        return@runOnUiThread
                                    }
                                    message = "Original saved. Enhancement queued…"
                                    val request = OneTimeWorkRequestBuilder<EnhanceWorker>()
                                        .setInputData(workDataOf(EnhanceWorker.KEY_URI to uri.toString()))
                                        .setConstraints(
                                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                                        )
                                        .build()
                                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                                    WorkManager.getInstance(this@MainActivity).getWorkInfoByIdLiveData(request.id)
                                        .observe(this@MainActivity) { info ->
                                            when (info?.state) {
                                                WorkInfo.State.RUNNING -> message = "Enhancing in background…"
                                                WorkInfo.State.SUCCEEDED -> message = "Enhanced photo saved to Pictures/API Enhance Camera/Enhanced"
                                                WorkInfo.State.FAILED -> message = "Enhancement failed. Original is safe. Check backend setup."
                                                WorkInfo.State.ENQUEUED -> message = "Original saved. Waiting for network…"
                                                else -> Unit
                                            }
                                        }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                runOnUiThread { busy = false; message = "Capture failed: ${exception.message}" }
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text("Capture Photo") }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
