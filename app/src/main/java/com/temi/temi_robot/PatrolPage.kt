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
import com.robotemi.sdk.constants.HardButton

class PatrolPage : Fragment(), RobotController.RequestReadyCallback{

    private lateinit var robotController: RobotController
    private lateinit var adapter: SimpleAdapter

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
        adapter = (activity as MainActivity).adapter!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_patrol, container, false)

        // Hide top bar
        robotController.hideTopBar()

        // Set Callback to listen to user request event
        robotController.setRequestReadyCallback(this)

        // Robot behavior at initialization
        initBehavior()

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        val settingsButton = view.findViewById<ImageButton>(R.id.settingsButton)

        // User button behavior
        interactionButton.setOnClickListener{
            robotController.setDetectionModeOn(false, 0.5f)
            robotController.setLastRequestTimeNow()
            robotController.stopMovement()
            robotController.resetInactivityTimer()
            robotController.askQuestion("Hi, how can I help you ?")
        }

        // Settings button behavior
        settingsButton.setOnClickListener {

            // Stop movement while on setting page
            robotController.stopMovement()
            robotController.setBlockMode(true)

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PasswordPage())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    // Robot behavior on start
    fun initBehavior(){
        robotController.setLocations(adapter.getItems())
        robotController.setBlockMode(false)
        robotController.patrol(robotController.getLocations())
        robotController.hideTopBar()
        robotController.setVolume(4)
        robotController.toggleWakeup(true)
        robotController.setTopBadgeEnabled(false)
        robotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        robotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
        robotController.setLastRequestTimeNow()
    }

    override fun onRequestIsReady(request: String) {
        (activity as MainActivity).userRequest = request
        // Change view to loading page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoadingPage())
            .addToBackStack(null)
            .commit()
    }

}