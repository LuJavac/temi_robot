package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.JsonManager
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.dataclasses.PatrolStates
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController

// Class for FirstPage when app is just opened
class FirstPage : Fragment(), RobotController.RobotReadyCallback, RobotController.MapReadyCallback{

    // Creates view for page
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_start, container, false)

        // Hide top bar
        RobotController.hideTopBar()

        // Set Callback to listen to robot and map ready events
        RobotController.setRobotReadyCallback(this)
        RobotController.setMapReadyCallback(this)

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons
        val startButton = view.findViewById<Button>(R.id.startButton)
        val settingsButton = view.findViewById<Button>(R.id.settingsButton)

        // Defining arguments for navigation
        val passwordPage = PasswordPage()
        val args = Bundle()

        // User button behavior
        startButton.setOnClickListener{
            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatrolPage())
                .addToBackStack(null)
                .commit()
        }

        // Settings button behavior
        settingsButton.setOnClickListener {
            // Stop movement while on setting page
            RobotController.stopMovement()
            RobotController.setBlockMode(true)

            // Passing argument to password page to know where we come from
            args.putString("from", "patrolSettings")
            passwordPage.arguments = args

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }
        return view
    }

    // When robot is initialized, load saved patrol states if file exists or load map
    override fun onRobotIsReady() {
        if(RobotController.askRequiredPermissions()){
            RobotController.setBlockMode(true)
            val savedState = JsonManager.restoreFromFile<PatrolStates>(requireContext(), (activity as MainActivity).savePatrolStatesFileName)
            if(savedState == null){
                RobotController.loadMap()
            } else {
                RobotController.setPatrolStates(savedState)
            }
        } else {
            // Change view to restart page if need to ask new permissions
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RestartPage())
                .addToBackStack(null)
                .commit()
        }
    }


    // When map is loaded check if it has valid data or not. If yes, load locations
    override fun onMapIsReady() {
        if(RobotController.getPatrolStates().getAllLocations().isEmpty()){
            RobotController.speak("I couldn't find the map or it has no locations. Please check the map name or add locations to your map.")
            // Change view to restart asking page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RestartPage())
                .addToBackStack(null)
                .commit()
            return
        }
    }



}