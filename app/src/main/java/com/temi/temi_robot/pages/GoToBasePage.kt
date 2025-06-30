package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController

class GoToBasePage : Fragment(), RobotController.BackToMainPageCallback {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_go_base, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide top bar
        RobotController.hideTopBar()

        // Set Callback to listen to back to main page event
        RobotController.setBackToMainPageCallback(this)

        // Disable detection mode when going home
        RobotController.setDetectionModeOn(false, 0.5f)

        // Adding button just for decoration in that case
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        interactionButton.setOnClickListener{
        }
    }

    // Callback to go back to main page when needed
    override fun onBackToMainPage() {
        // Change view to main page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainPage())
            .addToBackStack(null)
            .commit()
    }

}