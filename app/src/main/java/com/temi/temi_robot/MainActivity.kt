package com.temi.temi_robot

import android.os.Bundle
import androidx.activity.ComponentActivity

/////////////////////////////////////////////////////////

import android.widget.Button
import android.widget.Toast

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.start_button)

        startButton.setOnClickListener{
            Toast.makeText(this, "Bouton cliqué !", Toast.LENGTH_SHORT).show()
        }

    }


}