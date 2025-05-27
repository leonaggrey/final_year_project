package com.example.seee

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkUtils {

    // OkHttp client with extended timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val UPLOAD_URL = "http://my-new-yoloVersion3-loadbalancer-2047235041.eu-north-1.elb.amazonaws.com/upload"
    private const val TEXT_SERVER_URL = "http://proxy-server-version2-792917752.eu-north-1.elb.amazonaws.com/latest"

    fun sendImageToServer(imageData: ByteArray) {
        val compressedImageData = compressImage(imageData)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "image.jpg",
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), compressedImageData)
            )
            .build()

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkUtils", "Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("NetworkUtils", "Upload successful: ${response.body?.string()}")
                } else {
                    Log.e("NetworkUtils", "Upload error: ${response.code}")
                }
            }
        })
    }

    // Compress JPEG image to stay under ~1MB
    private fun compressImage(imageData: ByteArray): ByteArray {
        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val outputStream = ByteArrayOutputStream()

        var quality = 90
        do {
            outputStream.reset()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 10
        } while (outputStream.size() > 1_000_000 && quality > 10)

        return outputStream.toByteArray()
    }

    fun startPollingText(
        intervalMs: Long = 1000L,
        scope: CoroutineScope,
        onNewText: (String) -> Unit
    ) {

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(TEXT_SERVER_URL)
                        .build()

                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Log.e("NetworkUtils", "Polling failed: HTTP ${response.code}")
                        response.close()
                        delay(intervalMs)
                        continue
                    }

                    val bodyString = response.body?.string()
                    response.close()

                    if (!bodyString.isNullOrBlank()) {
                        val json = JSONObject(bodyString)
                        val newText = json.optString("text", "")

                        if (newText.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                onNewText(newText)
                            }
                        }
                    } else {
                        Log.e("NetworkUtils", "Empty or invalid JSON response")
                    }
                } catch (e: Exception) {
                    Log.e("NetworkUtils", "Polling error: ${e.localizedMessage}")
                }

                delay(intervalMs)
            }
        }
    }

}
