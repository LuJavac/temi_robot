package com.temi.temi_robot

import android.os.Bundle
import androidx.activity.ComponentActivity

import android.widget.Button
import android.widget.ImageView
import com.robotemi.sdk.permission.Permission


public class SpeechControl : ComponentActivity() {
    private val locations = listOf("test point 3","johan", "jason")
    private val robotController = RobotController(locations)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nypLogo = findViewById<ImageView>(R.id.my_gif)
        val startButton = findViewById<Button>(R.id.start_button)

        startButton.setOnClickListener{
            robotController.setDetectionModeOn(false, 0.5f)
            robotController.stopMovement()
            robotController.askQuestion("Hi, how can I help you ?")
        }
    }

    override fun onStart() {
        super.onStart()
        robotController.patrol(locations)
        robotController.setDetectionModeOn(true, 0.5f)
    }
}