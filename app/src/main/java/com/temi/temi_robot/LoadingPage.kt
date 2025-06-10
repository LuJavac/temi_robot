package com.temi.temi_robot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoadingPage : Fragment(), RobotController.BackToPatrolCallback {
    private lateinit var robotController: RobotController
    private lateinit var request: String

    val client = OkHttpClient()

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
        request = (activity as MainActivity).userRequest!!

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.layout_loading, container, false)

        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            LoadingScreen()
        }

        robotController.setBackToPatrolCallback(this)

        sendRequestToServer(request)

        return view
    }

    override fun onBackToPatrol() {
        // Change view to patrol page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PatrolPage())
            .addToBackStack(null)
            .commit()
    }

    fun sendRequestToServer(request: String) {
        val json = JSONObject()
        json.put("text", request)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://192.168.1.20:5000/process") // Replace with server URL
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                robotController.speak("Error sending data to server")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val bodyString = it.body?.string()
                        if (bodyString != null) {
                            val jsonResponse = JSONObject(bodyString)
                            val responseText = jsonResponse.getString("response")
                            robotController.speak(responseText)
                        } else {
                            robotController.speak("I have nothing to answer")
                        }
                    } else {
                        robotController.speak("Server error")
                    }
                }
            }
        })
    }
}