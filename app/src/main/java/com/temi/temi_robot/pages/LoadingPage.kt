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

    // Sending user request to python server
    fun sendRequestToServer(request: String) {
        val json = JSONObject()
        json.put("text", request)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url((activity as MainActivity).serverUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            // Behavior when failing to send data to server
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    RobotController.speak("Sorry I couldn't send data to the server")
                }
            }

            // Speaking server response or the error message
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val answerToDisplay: String

                    // On vérifie ce que le serveur Python a répondu
                    if (it.isSuccessful) {
                        val bodyString = it.body?.string()
                        if (bodyString != null) {
                            val jsonResponse = JSONObject(bodyString)
                            answerToDisplay = jsonResponse.getString("response")
                        } else {
                            answerToDisplay = "I have nothing to answer"
                        }
                    } else {
                        answerToDisplay = "The server has an error"
                    }

                    // Les changements d'écran doivent se faire sur le Thread Principal d'Android
                    requireActivity().runOnUiThread {
                        // 2. On prépare la MainPage en lui glissant la réponse dans les poches
                        val mainPage = MainPage()
                        val args = Bundle()
                        args.putString("answer", answerToDisplay)
                        args.putString("notPatrolAgain", "true")
                        args.putBoolean("startSpeaking", true)
                        mainPage.arguments = args

                        // 3. On affiche la MainPage
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, mainPage)
                            .commit()
                    }
                }
            }
        })
    }
}