package com.temi.temi_robot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment

class PatrolPage : Fragment(){

    private lateinit var robotController: RobotController

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.patrol_layout, container, false)

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Items of next interface
        val startButton = view.findViewById<Button>(R.id.interactionButton)

        // User button behavior
        startButton.setOnClickListener{
            robotController.setDetectionModeOn(false, 0.5f)
            robotController.setLastRequestTimeNow()
            robotController.stopMovement()
            robotController.resetInactivityTimer()
            robotController.askQuestion("Hi, how can I help you ?")
        }
        return view
    }
}