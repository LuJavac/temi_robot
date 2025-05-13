package com.temi.temi_robot

import android.os.Bundle
import androidx.activity.ComponentActivity

import android.widget.Button
import android.widget.ImageView


public class SpeechControl : ComponentActivity() {
    private val robotController = RobotController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nypLogo = findViewById<ImageView>(R.id.my_gif)
        val startButton = findViewById<Button>(R.id.start_button)

        startButton.setOnClickListener{
            robotController.askQuestion("Hi, how can I help you ?")
        }

    }
}