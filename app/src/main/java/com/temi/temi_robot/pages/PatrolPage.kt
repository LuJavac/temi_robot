package com.temi.temi_robot.pages

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.robotemi.sdk.constants.HardButton
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import androidx.core.content.edit

// Page to display when the robot is patrolling
class PatrolPage : Fragment(), RobotController.RequestReadyCallback, RobotController.MeetingStartedCallback{

    private lateinit var robotController: RobotController
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        robotController = (activity as MainActivity).robotController
        connectivityManager = (activity as MainActivity).connectivityManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_patrol, container, false)

        // Going to lost connection page when Wi-Fi is disconnected and moving the robot to home base
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)

                // Sending temi to home base
                robotController.sendTemiToHomeBase()

                // Change view to lost connection page
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LostConnectionPage())
                    .addToBackStack(null)
                    .commit()
            }
        }

        // Registering callback to detect system Wi-FI changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)


        // Hide top bar
        robotController.hideTopBar()

        // Set Callback to listen to user request event
        robotController.setRequestReadyCallback(this)

        // Set Callback to listen to meeting started event
        robotController.setMeetingStartedCallback(this)

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
        robotController.setBlockMode(false)
        robotController.patrol()
        robotController.hideTopBar()
        robotController.setVolume(4)
        robotController.toggleWakeup(true)
        robotController.setTopBadgeEnabled(false)
        robotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        robotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
        robotController.setLastRequestTimeNow()
    }

    // When user request arrived, change view to loading page
    override fun onRequestIsReady(request: String) {
        (activity as MainActivity).userRequest = request
        // Change view to loading page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoadingPage())
            .addToBackStack(null)
            .commit()
    }

    // When meeting started, save last page before leaving for call
    override fun onMeetingStarted() {
        // Save last page before leaving for call
        val prefs = requireActivity().getSharedPreferences("temi_state", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_fragment", this::class.java.name)
                .putBoolean("should_restore_fragment", true)
        }
    }

    // Unregistering callback to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

}