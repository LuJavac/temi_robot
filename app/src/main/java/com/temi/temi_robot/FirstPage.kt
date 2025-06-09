package com.temi.temi_robot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.Json
import java.io.File

class FirstPage : Fragment(), RobotController.RobotReadyCallback, RobotController.MapReadyCallback{

    private lateinit var robotController: RobotController
    private lateinit var adapter: SimpleAdapter
    private lateinit var locations: MutableList<String>

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
            val savedState = Json.decodeFromString<PatrolState>(json)
            val adapter = SimpleAdapter(savedState.items.toMutableList())
            adapter.restoreStates(savedState.itemStates)
            (activity as MainActivity).adapter = adapter
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

    // When map is loaded check if it has valid data or not
    override fun onMapIsReady() {
        if(robotController.getLocations().isEmpty()){
            robotController.speak("I couldn't find the map or it has no locations. Please check the map name or add locations to your map.")
            // Change view to restart asking page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RestartPage())
                .addToBackStack(null)
                .commit()
            return
        }
        locations = robotController.getLocations().filter{it.lowercase() != "home base"}.toMutableList()
        adapter = SimpleAdapter(locations)
        (activity as MainActivity).adapter = adapter
    }



}