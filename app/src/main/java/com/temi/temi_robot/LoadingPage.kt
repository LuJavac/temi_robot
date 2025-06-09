package com.temi.temi_robot

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

class LoadingPage : Fragment() {
    val client = OkHttpClient()

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
        return view
    }

    fun sendRequestToServer(request: String) {
        val json = JSONObject()
        json.put("text", request)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://192.168.1.10:5000/main3.py") // Replace with server URL
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Error : ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val bodyString = it.body?.string()
                        if (bodyString != null) {
                            val jsonResponse = JSONObject(bodyString)
                            val responseText = jsonResponse.getString("response")
                            println("Response from server: $responseText")
                        } else {
                            println("Response body is null")
                        }
                    } else {
                        println("Server error : ${it.code}")
                    }
                }
            }
        })
    }
}