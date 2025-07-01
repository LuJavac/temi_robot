package com.temi.temi_robot.pages

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import com.robotemi.sdk.constants.HardButton
import com.temi.temi_robot.time.AlarmScheduler
import com.temi.temi_robot.JsonManager
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.dataclasses.PatrolStates
import com.temi.temi_robot.dataclasses.TimeSlot

// Class for FirstPage when app is just opened
class FirstPage : Fragment(), RobotController.RobotReadyCallback, RobotController.MapReadyCallback {

    private lateinit var alarmScheduler: AlarmScheduler

    // Recover alarm scheduler from main activity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        alarmScheduler = (activity as MainActivity).alarmScheduler
    }

    // Creates view for page
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_first, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide top bar
        RobotController.hideTopBar()

        // Set Callback to listen to robot and map ready events
        RobotController.setRobotReadyCallback(this)
        RobotController.setMapReadyCallback(this)

        // Buttons (not visible until everything is loaded)
        val yesButton = view.findViewById<Button>(R.id.yesButton)
        val noButton = view.findViewById<Button>(R.id.noButton)
        changeItemsVisibility(View.GONE)

        // If at home base you can start asking questions on the main page
        yesButton.setOnClickListener {
            // Change view to main page if robot at home base
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainPage())
                .addToBackStack(null)
                .commit()
        }

        // If not at home base, initialize by going to home base
        noButton.setOnClickListener {
            RobotController.speak("I need to go to home base to initialize")
            RobotController.goToHomeBase()
            // Go to base if not at home base
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GoToBasePage())
                .addToBackStack(null)
                .commit()
        }
    }

    // When robot is initialized, load saved patrol states if file exists or load map
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onRobotIsReady() {

        initSystem()

        if(RobotController.askRequiredPermissions()){
            RobotController.setBlockMode(true)
            val savedState = JsonManager.restoreFromFile<PatrolStates>(requireContext(), (activity as MainActivity).savePatrolStatesFileName)
            val savedSlots = JsonManager.restoreFromFile<List<TimeSlot>>(requireContext(), (activity as MainActivity).saveTimeSlotsFileName)
            if(savedSlots != null){
                alarmScheduler.setAllAlarms(savedSlots)
            }
            if(savedState == null){
                RobotController.loadMap()
            } else {
                RobotController.setPatrolStates(savedState)
                changeItemsVisibility(View.VISIBLE)
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
        changeItemsVisibility(View.VISIBLE)
    }

    // Change the visibility of the page's items
    fun changeItemsVisibility(visibility: Int) {
        val yesButton = view?.findViewById<Button>(R.id.yesButton)
        val noButton = view?.findViewById<Button>(R.id.noButton)
        val text = view?.findViewById<TextView>(R.id.yesOrNoText)
        yesButton?.visibility = visibility
        noButton?.visibility = visibility
        text?.visibility = visibility
    }

    // Setting system default parameters
    fun initSystem(){
        RobotController.setVolume(4)
        RobotController.toggleWakeup(true) //s Disable wake-up sentences when at true
        RobotController.setTopBadgeEnabled(true) // CHANGE TO FALSE
        RobotController.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED) // CHANGE TO DISABLED
        RobotController.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
    }
}