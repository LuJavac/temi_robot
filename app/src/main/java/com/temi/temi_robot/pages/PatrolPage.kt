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
import com.robotemi.sdk.Robot

// Page to display when the robot is patrolling
class PatrolPage : Fragment(), RobotController.RequestReadyCallback, RobotController.MeetingStartedCallback, RobotController.BackToBaseCallback{
    
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Recover robot controller from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        connectivityManager = (activity as MainActivity).connectivityManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_patrol, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Going to lost connection page when Wi-Fi is disconnected and moving the robot to home base
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)

                // Sending temi to home base
                RobotController.goToHomeBase()

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
        RobotController.hideTopBar()

        // Set Callback to listen to user request event
        RobotController.setRequestReadyCallback(this)

        // Set Callback to listen to meeting started event
        RobotController.setMeetingStartedCallback(this)

        // Set Callback to listen to back to base event
        RobotController.setBackToBaseCallback(this)

        // Robot behavior at initialization
        initBehavior()

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        val settingsButton = view.findViewById<ImageButton>(R.id.settingsButton)
        val timeButton = view.findViewById<ImageButton>(R.id.timeButton)

        // Defining arguments for navigation
        val passwordPage = PasswordPage()
        val args = Bundle()

        // User button behavior
        interactionButton.setOnClickListener{
            RobotController.setDetectionModeOn(false, 0.5f)
            RobotController.setLastRequestTimeNow()
            RobotController.stopMovement()
            RobotController.resetInactivityTimer()
            RobotController.askQuestion("Hi, how can I help you ?")
        }

        // Settings button behavior
        settingsButton.setOnClickListener {
            // Passing argument to password page to know where we come from
            args.putString("from", "locationsSettings")
            passwordPage.arguments = args

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }

        // Time button behavior
        timeButton.setOnClickListener {

            // Passing argument to password page to know where we come from
            args.putString("from", "timeSettings")
            passwordPage.arguments = args

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }

    }

    // Robot behavior on start
    fun initBehavior(){
        // To know if we got triggered by an alarm or not
        val isAlarm = arguments?.getString("isAlarm")

        if(!RobotController.isAtHomeBase()){
            RobotController.setBlockMode(false)
            // If we got triggered by an alarm we don't need to patrol again (avoiding bugs)
            if(isAlarm != "true"){
                RobotController.patrol()
            }
            RobotController.startPeriodicSpeech(1)
        }
        RobotController.setVolume(4)
        RobotController.toggleWakeup(true)
        RobotController.setTopBadgeEnabled(true) // CHANGE TO FALSE
        RobotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        RobotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
        RobotController.setLastRequestTimeNow()
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

    // Callback to go back to base page when needed
    override fun onBackToBase() {
        // Change view to go back base page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, GoToBasePage())
            .addToBackStack(null)
            .commit()
    }

    // Unregistering callback to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

}