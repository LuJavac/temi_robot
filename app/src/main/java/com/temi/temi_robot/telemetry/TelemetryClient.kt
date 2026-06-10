package com.temi.temi_robot.telemetry

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Client de télémétrie : envoie des événements anonymes (waves, détections,
 * sessions de conversation) à l'API d'ingestion hébergée sur le Raspberry Pi.
 *
 * Confidentialité : on n'envoie JAMAIS de verbatim ni d'identifiant nominatif,
 * uniquement des compteurs, durées et horodatages. Les session_id sont des
 * UUID aléatoires non reliables à une personne.
 *
 * Bufferisation hors-ligne : chaque événement est d'abord écrit dans un
 * fichier local (une ligne JSON par événement, horodatée au moment de
 * l'événement), puis un flush périodique envoie le contenu du fichier dès
 * que l'API est joignable. Rien n'est perdu pendant les coupures réseau.
 */
object TelemetryClient {
    private const val TAG = "TelemetryClient"

    // IP : RaspBerry (même machine que serverUrl dans MainActivity)
    private const val ENDPOINT_URL = "http://192.168.1.43:8000/events/batch"
    // Jeton lu depuis local.properties (telemetry.token=...), jamais versionné.
    // Doit correspondre à API_TOKEN dans temi-telemetry/.env sur le Pi.
    private val TELEMETRY_TOKEN = com.temi.temi_robot.BuildConfig.TELEMETRY_TOKEN

    private const val QUEUE_FILE_NAME = "telemetry_queue.jsonl"
    private const val FLUSH_INTERVAL_MS = 30_000L
    private const val MAX_QUEUED_EVENTS = 1000
    private const val MAX_BATCH_SIZE = 200

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fileLock = Any()
    private var queueFile: File? = null
    private var flushHandler: Handler? = null

    // --- Session de conversation en cours (UUID anonyme) ---
    private var sessionId: String? = null
    private var sessionStartMs: Long = 0
    private var sessionTurnCount: Int = 0

    /** À appeler une fois au démarrage de l'app (MainActivity.onCreate). */
    fun init(context: Context) {
        if (queueFile != null) return
        if (TELEMETRY_TOKEN.isBlank()) {
            Log.w(TAG, "telemetry.token absent de local.properties — l'API refusera les envois (401)")
        }
        queueFile = File(context.applicationContext.filesDir, QUEUE_FILE_NAME)

        val thread = HandlerThread("telemetry-flush").apply { start() }
        flushHandler = Handler(thread.looper)
        scheduleFlush(0L)
    }

    /** Événement ponctuel : "wave", "face_detected", "greeting", "idle", ... */
    fun track(eventType: String) {
        val event = JSONObject()
            .put("event_type", eventType)
            .put("timestamp", isoFormat.format(Date()))
        enqueue(event)
    }

    /**
     * Marque un tour de conversation (une question posée à l'IA).
     * Ouvre une session anonyme si aucune n'est en cours.
     */
    @Synchronized
    fun recordConversationTurn() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString()
            sessionStartMs = System.currentTimeMillis()
            sessionTurnCount = 0
        }
        sessionTurnCount++
    }

    /**
     * Clôt la session en cours (inactivité, mise en veille, sortie de page)
     * et envoie ses métriques : durée + nombre de tours. Sans verbatim.
     */
    @Synchronized
    fun endSession() {
        val id = sessionId ?: return
        val durationS = (System.currentTimeMillis() - sessionStartMs) / 1000.0
        val event = JSONObject()
            .put("event_type", "conversation")
            .put("timestamp", isoFormat.format(Date()))
            .put("session_id", id)
            .put("duration_s", durationS)
            .put("turn_count", sessionTurnCount)
        sessionId = null
        sessionTurnCount = 0
        enqueue(event)
    }

    // --- File d'attente hors-ligne ---

    private fun enqueue(event: JSONObject) {
        val file = queueFile ?: run {
            Log.w(TAG, "Telemetry not initialized, event dropped: $event")
            return
        }
        synchronized(fileLock) {
            try {
                val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
                lines.add(event.toString())
                // Borne la taille de la file : on jette les plus anciens
                while (lines.size > MAX_QUEUED_EVENTS) lines.removeAt(0)
                file.writeText(lines.joinToString("\n") + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue telemetry event", e)
            }
        }
    }

    private fun scheduleFlush(delayMs: Long) {
        flushHandler?.postDelayed({
            try {
                flush()
            } catch (e: Exception) {
                Log.e(TAG, "Telemetry flush failed", e)
            }
            scheduleFlush(FLUSH_INTERVAL_MS)
        }, delayMs)
    }

    private fun flush() {
        val file = queueFile ?: return

        // Photographie du début de la file (enqueue ne fait qu'ajouter à la fin,
        // donc on pourra retirer les N premières lignes sans rien perdre)
        val batch: List<String>
        synchronized(fileLock) {
            if (!file.exists()) return
            batch = file.readLines().filter { it.isNotBlank() }.take(MAX_BATCH_SIZE)
        }
        if (batch.isEmpty()) return

        val payload = JSONArray()
        for (line in batch) {
            try {
                payload.put(JSONObject(line))
            } catch (e: Exception) {
                Log.e(TAG, "Corrupted telemetry line skipped", e)
            }
        }

        val request = Request.Builder()
            .url(ENDPOINT_URL)
            .header("Authorization", "Bearer $TELEMETRY_TOKEN")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Telemetry API answered ${response.code}, keeping events queued")
                return
            }
        }

        // Envoi réussi : on retire du fichier les lignes envoyées
        synchronized(fileLock) {
            val remaining = file.readLines().filter { it.isNotBlank() }.drop(batch.size)
            if (remaining.isEmpty()) {
                file.delete()
            } else {
                file.writeText(remaining.joinToString("\n") + "\n")
            }
        }
        Log.d(TAG, "Flushed ${batch.size} telemetry events")
    }
}
