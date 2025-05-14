package com.temi.temi_robot

import android.os.Bundle
import androidx.activity.ComponentActivity

import android.widget.Button
import android.widget.ImageView
import com.robotemi.sdk.permission.Permission


public class SpeechControl : ComponentActivity() {
    private val robotController = RobotController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nypLogo = findViewById<ImageView>(R.id.my_gif)
        val startButton = findViewById<Button>(R.id.start_button)

        val locations = listOf("jason", "test point 3", "me3")

        startButton.setOnClickListener{
            //robotController.patrol(locations)
            robotController.askQuestion("Hi, how can I help you ?")
        }

    }
}