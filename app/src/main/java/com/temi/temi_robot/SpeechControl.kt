package com.temi.temi_robot

import android.os.Bundle
import androidx.activity.ComponentActivity

import android.widget.Button
import com.robotemi.sdk.constants.Gender


public class SpeechControl : ComponentActivity() {
    private val robotController = RobotController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Robot voice settings : speed 0.5f-2.0f and pitch -10-10
        robotController.setTtsVoice(Gender.FEMALE, 2.0f, 5)

        val startButton = findViewById<Button>(R.id.start_button)

        startButton.setOnClickListener{
            robotController.askQuestion("Hi, do you need any help ?")
        }

    }
}