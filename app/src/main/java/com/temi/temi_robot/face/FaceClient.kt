package com.temi.temi_robot.face

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client de reconnaissance faciale : envoie des images au service `temi-face`
 * hébergé sur le Raspberry Pi (FastAPI + InsightFace), et récupère le prénom
 * de la personne reconnue.
 *
 * Confidentialité : seules des IMAGES transitent (jamais stockées côté tablette).
 * Le Pi n'en garde QUE l'empreinte (vecteur 512-d non réversible), pas la photo.
 *
 * Tous les appels sont asynchrones ; les callbacks sont délivrés sur le thread
 * principal pour pouvoir toucher l'UI / faire parler le robot directement.
 */
object FaceClient {
    private const val TAG = "FaceClient"

    // IP : Raspberry (même machine que serverUrl dans MainActivity). Port du service face.
    private const val BASE_URL = "http://192.168.1.8:8001"

    private val jpegMediaType = "image/jpeg".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)  // l'inférence Pi 4 prend ~0,3-1 s
        .build()

    /** Résultat d'une reconnaissance. `match=false` => raison dans `reason`. */
    data class RecognitionResult(
        val match: Boolean,
        val name: String?,
        val personId: Int?,
        val score: Double,
        val reason: String?,
    )

    /**
     * Reconnaît la personne sur une image JPEG (le visage le plus proche).
     * Le callback reçoit le résultat, ou `null` en cas d'échec réseau.
     */
    fun recognize(jpeg: ByteArray, onResult: (RecognitionResult?) -> Unit) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "face.jpg", jpeg.toRequestBody(jpegMediaType))
            .build()
        val request = Request.Builder().url("$BASE_URL/recognize").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "recognize: échec réseau", e)
                postMain { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use { parseRecognition(it) }
                postMain { onResult(result) }
            }
        })
    }

    /** Résultat d'un enrôlement : nb de photos retenues / rejetées, ou échec. */
    data class EnrollResult(
        val success: Boolean,
        val personId: Int?,
        val enrolledPhotos: Int,
        val rejectedPhotos: Int,
        val error: String?,
    )

    /**
     * Enrôle une nouvelle personne avec plusieurs images (multi-angles).
     * Le callback reçoit le résultat sur le thread principal.
     */
    fun enroll(name: String, jpegs: List<ByteArray>, onResult: (EnrollResult) -> Unit) {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
        jpegs.forEachIndexed { i, jpeg ->
            builder.addFormDataPart("images", "face$i.jpg", jpeg.toRequestBody(jpegMediaType))
        }
        val request = Request.Builder().url("$BASE_URL/enroll").post(builder.build()).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "enroll: échec réseau", e)
                postMain { onResult(EnrollResult(false, null, 0, 0, "network")) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use { parseEnroll(it) }
                postMain { onResult(result) }
            }
        })
    }

    private fun parseRecognition(response: Response): RecognitionResult? {
        val text = response.body?.string() ?: return null
        return try {
            val json = JSONObject(text)
            RecognitionResult(
                match = json.optBoolean("match", false),
                name = if (json.isNull("name")) null else json.getString("name"),
                personId = if (json.has("person_id")) json.optInt("person_id") else null,
                score = json.optDouble("score", 0.0),
                reason = if (json.has("reason")) json.optString("reason") else null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "recognize: réponse illisible: $text", e)
            null
        }
    }

    private fun parseEnroll(response: Response): EnrollResult {
        val text = response.body?.string() ?: ""
        return try {
            val json = JSONObject(text)
            if (response.isSuccessful) {
                EnrollResult(
                    success = true,
                    personId = if (json.has("person_id")) json.optInt("person_id") else null,
                    enrolledPhotos = json.optInt("enrolled_photos", 0),
                    rejectedPhotos = json.optInt("rejected_photos", 0),
                    error = null,
                )
            } else {
                // FastAPI renvoie {"detail": "..."} en cas d'erreur (422, etc.)
                EnrollResult(false, null, 0, 0, json.optString("detail", "http_${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "enroll: réponse illisible: $text", e)
            EnrollResult(false, null, 0, 0, "parse_error")
        }
    }

    private fun postMain(action: () -> Unit) = mainHandler.post(action)
}
