package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.ui_utils.LoadingScreen
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// Class for loading page when sending requests to python server
class LoadingPage : Fragment(), RobotController.BackToPatrolCallback {
    private lateinit var robotController: RobotController
    private lateinit var request: String

    val client = OkHttpClient() // Client for sending requests to server

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
        request = (activity as MainActivity).userRequest!!

    }

    // Creates the view for the page
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Main layout
        val view = inflater.inflate(R.layout.layout_loading, container, false)

        // Part of the view for animated loading indicator
        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            LoadingScreen()
        }

        // Hide top bar
        robotController.hideTopBar()

        // Set Callback to listen to when going back to patrol page
        robotController.setBackToPatrolCallback(this)

        // Send user request to server
        sendRequestToServer(request)

        return view
    }

    // Callback override to go back to patrol page when triggered
    override fun onBackToPatrol() {
        // Change view to patrol page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PatrolPage())
            .addToBackStack(null)
            .commit()
    }

    // Sending user request to python server
    fun sendRequestToServer(request: String) {
        val json = JSONObject()
        json.put("text", request)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://192.168.142.124:5000/process") // Replace with server URL
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            // Behavior when failing to send data to server
            override fun onFailure(call: Call, e: IOException) {
                robotController.speak("Sorry I couldn't send data to the server")
            }

            // Speaking server response or the error message
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
                        robotController.speak("The server has an error")
                    }
                }
            }
        })
    }
}