package com.example.seee


import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.io.File
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.InputStream

class CloudTTSHelper(private val context: Context) {
    private val client = OkHttpClient()

    fun speak(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accessToken = getAccessToken()
                val json = JSONObject().apply {
                    put("input", JSONObject().put("text", text))
                    put("voice", JSONObject()
                        .put("languageCode", "en-US")
                        .put("name", "en-US-Wavenet-F")) // Change voice here
                    put("audioConfig", JSONObject()
                        .put("audioEncoding", "MP3"))
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())
                val request = Request.Builder()
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body?.string() ?: "")
                val audioContent = responseJson.getString("audioContent")
                playAudio(audioContent)

            } catch (e: Exception) {
                Log.e("CloudTTS", "Error: ${e.message}", e)
            }
        }
    }

    private fun getAccessToken(): String {
        val assetManager = context.assets
        val inputStream: InputStream = assetManager.open("second-terrain-450816-n5-fde9f251b287.json")
        val credentials = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }

    private fun playAudio(base64Audio: String) {
        val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
        val tempFile = File.createTempFile("tts", ".mp3", context.cacheDir)
        tempFile.writeBytes(audioBytes)

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(tempFile.absolutePath)
            prepare()
            start()
        }

        mediaPlayer.setOnCompletionListener {
            tempFile.delete()
            mediaPlayer.release()
        }
    }
}
