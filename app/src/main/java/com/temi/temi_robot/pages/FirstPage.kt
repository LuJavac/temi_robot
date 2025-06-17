package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.PatrolStates
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.SimpleAdapter
import kotlinx.serialization.json.Json
import java.io.File

class FirstPage : Fragment(), RobotController.RobotReadyCallback, RobotController.MapReadyCallback{

    private lateinit var robotController: RobotController

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
    }

    // Creates view for page
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_start, container, false)

        // Hide top bar
        robotController.hideTopBar()

        // Set Callback to listen to robot and map ready events
        robotController.setRobotReadyCallback(this)
        robotController.setMapReadyCallback(this)

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons
        val startButton = view.findViewById<Button>(R.id.startButton)
        val settingsButton = view.findViewById<Button>(R.id.settingsButton)

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
            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PasswordPage())
                .addToBackStack(null)
                .commit()
        }
        return view
    }

    // When robot is initialized, load saved patrol states if file exists or load map
    override fun onRobotIsReady() {
        if(robotController.askRequiredPermissions()){
            robotController.setBlockMode(true)
            if(!restoreFromFile((activity as MainActivity).savePatrolStatesFileName)){
                robotController.loadMap()
            }
        } else {
            // Change view to restart page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RestartPage())
                .addToBackStack(null)
                .commit()
        }
    }

    // Restore patrol states from saved file
    private fun restoreFromFile(fileName: String): Boolean {
        val file = File(context?.filesDir, fileName)
        if (file.exists()) {
            val json = file.readText()
            val savedState = Json.decodeFromString<PatrolStates>(json)
            robotController.setPatrolStates(savedState)
            return true
        } else {
            return false
        }
    }

    // Delete patrol states file :: FOR TESTING PURPOSES ONLY
    private fun deleteFile(fileName: String) {
        val file = File(context?.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    // When map is loaded check if it has valid data or not. If yes, load locations
    override fun onMapIsReady() {
        if(robotController.getPatrolStates().getAllLocations().isEmpty()){
            robotController.speak("I couldn't find the map or it has no locations. Please check the map name or add locations to your map.")
            // Change view to restart asking page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RestartPage())
                .addToBackStack(null)
                .commit()
            return
        }
    }



}