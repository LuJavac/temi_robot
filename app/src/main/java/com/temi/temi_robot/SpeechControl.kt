package com.temi.temi_robot

import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.robotemi.sdk.constants.HardButton


public class SpeechControl : ComponentActivity(), RobotController.RobotReadyCallback {
    private val locations = listOf("test","jason", "johan")
    //private val locations = listOf("patrol centerwing", "patrol south corridor", "patrol south door", "patrol southwing", "patrol southwing entry", "patrol southwing back", "patrol southwing entry", "patrol southwing", "patrol south door", "patrol south corridor", "patrol centerwing",
    //                               "patrol north corridor", "patrol north door", "patrol northwing", "patrol northwing entry", "patrol northwing1", "patrol northwing1 middle", "patrol northwing1 back", "patrol northwing1 middle", "patrol northwing1", "patrol northwing entry", "patrol northwing2", "patrol northwing2 grass", "patrol northwing2 middle", "patrol northwing2 back", "patrol northwing2 middle", "patrol northwing2 grass", "patrol northwing2", "patrol northwing entry", "patrol northwing", "patrol north door", "patrol north corridor", "patrol centerwing")
    private lateinit var robotController: RobotController

    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaPlayer = MediaPlayer.create(this, R.raw.nga)

        // Initialize Chaquo Python
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }

        // Initialize Python file module
        val py = Python.getInstance()
        val module = py.getModule("main") // file name without .py
        robotController = RobotController(locations, module, mediaPlayer)

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
        if(robotController.askRequiredPermissions()){
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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

}
