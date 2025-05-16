package com.temi.temi_robot

import android.Manifest
import android.app.ActivityManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission


public class SpeechControl : ComponentActivity(), RobotController.RobotReadyCallback {
    private val locations = listOf("test point 3","johan", "jason")
    private val robotController = RobotController(locations)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nypLogo = findViewById<ImageView>(R.id.my_gif)
        val startButton = findViewById<Button>(R.id.start_button)

        robotController.setRobotReadyCallback(this)

        startButton.setOnClickListener{
            robotController.setDetectionModeOn(false, 0.5f)
            robotController.setLastRequestTimeNow()
            robotController.stopMovement()
            robotController.resetInactivityTimer()
            robotController.askQuestion("Hi, how can I help you ?")
        }
    }

    override fun onRobotIsReady() {
        robotController.patrol(locations)
    }

}
