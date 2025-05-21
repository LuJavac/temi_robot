package com.temi.temi_robot

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.robotemi.sdk.constants.HardButton


public class SpeechControl : ComponentActivity(), RobotController.RobotReadyCallback {
    private val locations = listOf("test","jason", "johan")
    private lateinit var robotController: RobotController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Chaquo Python
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }

        // Initialize Python file module
        val py = Python.getInstance()
        val module = py.getModule("main") // nom du fichier sans .py
        robotController = RobotController(locations, module)

        // Set Callback to listen to robot ready event
        robotController.setRobotReadyCallback(this)

        // Define interface view
        val nypLogo = findViewById<ImageView>(R.id.my_gif)
        val startButton = findViewById<Button>(R.id.start_button)

        // Button behavior
        startButton.setOnClickListener{
            robotController.setDetectionModeOn(false, 0.5f)
            robotController.setLastRequestTimeNow()
            robotController.stopMovement()
            robotController.resetInactivityTimer()
            robotController.askQuestion("Hi, how can I help you ?")
        }
    }

    override fun onRobotIsReady() {
        robotController.setDetectionModeOn(true, 0.5f)
        robotController.patrol(locations)
        robotController.hideTopBar()
        robotController.setVolume(4)
        robotController.toggleWakeup(true)
        robotController.setTopBadgeEnabled(false)
        robotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.DISABLED)
        robotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
    }

}
