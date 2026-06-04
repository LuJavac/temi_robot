package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Class for loading page when sending requests to python server
class LoadingPage : Fragment(), RobotController.BackToMainPageCallback {
    private lateinit var request: String

    val client = OkHttpClient.Builder() // Client for sending requests to server
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // --- VARIABLES POUR L'ANIMATION ---
    private val animationHandler = Handler(Looper.getMainLooper())
    private var currentStep = 0

    // Tampon des tokens reçus, en attente d'une phrase complète à faire lire
    private var pending = ""

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        request = (activity as MainActivity).userRequest!!
    }

    // Creates the view for the page
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Main layout (Assure-toi que ton fichier XML s'appelle bien layout_loading.xml)
        return inflater.inflate(R.layout.layout_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide top bar
        RobotController.hideTopBar()

        // Set Callback to listen to when going back to main page
        RobotController.setBackToMainPageCallback(this)

        // 🚀 Lancement de l'animation des 3 robots
        animationHandler.post(loadingAnimationRunnable)

        // Send user request to server
        sendRequestToServer(request)
    }

    // --- BOUCLE D'ANIMATION ---
    private val loadingAnimationRunnable = object : Runnable {
        override fun run() {
            // On récupère nos 3 images depuis le XML
            val robotRunning = view?.findViewById<ImageView>(R.id.robotRunning)
            val robotLoading = view?.findViewById<ImageView>(R.id.robotLoading)
            val robotIdea = view?.findViewById<ImageView>(R.id.robotIdea)

            // On cache tout le monde par défaut
            robotRunning?.visibility = View.INVISIBLE
            robotLoading?.visibility = View.INVISIBLE
            robotIdea?.visibility = View.INVISIBLE

            // On affiche uniquement celui de l'étape actuelle
            when (currentStep) {
                0 -> robotRunning?.visibility = View.VISIBLE
                1 -> robotLoading?.visibility = View.VISIBLE
                2 -> robotIdea?.visibility = View.VISIBLE
            }

            // On passe à l'étape suivante (0, 1, 2, puis on recommence à 0)
            currentStep = (currentStep + 1) % 3

            // On relance la fonction dans 1 seconde
            animationHandler.postDelayed(this, 1000)
        }
    }

    // Callback override to go back to main page when triggered
    override fun onBackToMainPage() {
        // Change view to main page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainPage())
            .addToBackStack(null)
            .commit()
    }

    // TRÈS IMPORTANT : On arrête l'animation quand la réponse du serveur arrive et qu'on quitte la page
    override fun onDestroyView() {
        super.onDestroyView()
        animationHandler.removeCallbacks(loadingAnimationRunnable)
    }

    // Sending user request to python server (streaming SSE)
    fun sendRequestToServer(request: String) {
        val json = JSONObject()
        json.put("text", request)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url((activity as MainActivity).streamUrl)
            .post(body)
            .build()

        client.newCall(httpRequest).enqueue(object : Callback {
            // Behavior when failing to send data to server
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    RobotController.speak("Sorry I couldn't send data to the server")
                }
            }

            // Read the SSE stream: speak each sentence as soon as it is complete,
            // and accumulate the full text to display once finished.
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        activity?.runOnUiThread { RobotController.speak("The server has an error") }
                        return
                    }

                    val full = StringBuilder()
                    try {
                        val source = it.body!!.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break

                            val delta = JSONObject(data).optString("delta", "")
                            if (delta.isEmpty()) continue

                            full.append(delta)
                            pending += delta
                            flushSentences(force = false) // parle dès qu'une phrase est prête
                        }
                        flushSentences(force = true) // lit le reliquat (dernière phrase)
                    } catch (e: Exception) {
                        // Flux interrompu : on lit ce qu'on a déjà, puis on continue
                        flushSentences(force = true)
                    }

                    // Une fois le flux terminé, on affiche la transcription complète
                    // (sans refaire parler : le robot a déjà tout dit en streaming).
                    activity?.runOnUiThread { goToMainPage(full.toString()) }
                }
            }
        })
    }

    // Extrait les phrases complètes du tampon et les envoie au TTS (qui les met en file).
    // On ne coupe qu'à une ponctuation SUIVIE d'un espace, pour ne pas casser "8.30".
    // force=true vide aussi le reliquat (fin de réponse, sans ponctuation finale).
    private fun flushSentences(force: Boolean) {
        while (true) {
            var idx = -1
            for (i in 0 until pending.length - 1) {
                val c = pending[i]
                if ((c == '.' || c == '!' || c == '?' || c == '\n') && pending[i + 1].isWhitespace()) {
                    idx = i
                    break
                }
            }
            if (idx == -1) break
            val sentence = pending.substring(0, idx + 1).trim()
            pending = pending.substring(idx + 1)
            if (sentence.isNotEmpty()) speakOnUi(sentence)
        }
        if (force && pending.isNotBlank()) {
            speakOnUi(pending.trim())
            pending = ""
        }
    }

    private fun speakOnUi(sentence: String) {
        activity?.runOnUiThread { RobotController.speak(sentence) }
    }

    private fun goToMainPage(answer: String) {
        if (!isAdded) return
        val mainPage = MainPage()
        val args = Bundle()
        args.putString("answer", answer)
        args.putString("notPatrolAgain", "true")
        args.putBoolean("startSpeaking", false) // déjà lu en streaming
        mainPage.arguments = args

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, mainPage)
            .commit()
    }
}