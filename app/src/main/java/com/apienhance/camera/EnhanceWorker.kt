package com.apienhance.camera

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class EnhanceWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val source = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return@withContext Result.failure()
        try {
            val input = applicationContext.contentResolver.openInputStream(source)
                ?: return@withContext Result.failure()
            val bytes = input.use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext Result.failure()

            val boundary = "----ApiEnhance${UUID.randomUUID()}"
            val connection = (URL(BuildConfig.ENHANCE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "image/jpeg")
            }
            connection.outputStream.use { out ->
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"image\"; filename=\"original.jpg\"\r\n".toByteArray())
                out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            if (connection.responseCode !in 200..299) {
                connection.errorStream?.close()
                connection.disconnect()
                return@withContext Result.retry()
            }
            val enhanced = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            if (enhanced.isEmpty()) return@withContext Result.retry()

            val name = "Enhanced_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/API Enhance Camera/Enhanced")
            }
            val uri = applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.retry()
            try {
                applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(enhanced) }
                    ?: throw IllegalStateException("Cannot open destination")
            } catch (e: Exception) {
                applicationContext.contentResolver.delete(uri, null, null)
                throw e
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object { const val KEY_URI = "original_uri" }
}
