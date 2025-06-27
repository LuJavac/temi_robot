package com.temi.temi_robot.pages

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import com.temi.temi_robot.AlarmScheduler
import com.temi.temi_robot.JsonManager
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.dataclasses.PatrolStates
import com.temi.temi_robot.dataclasses.TimeSlot

// Class for FirstPage when app is just opened
class FirstPage : Fragment(), RobotController.RobotReadyCallback, RobotController.MapReadyCallback {

    private lateinit var alarmScheduler: AlarmScheduler

    // Recover Alarm scheduler from main activity
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

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons (not visible until everything is loaded)
        val yesButton = view.findViewById<Button>(R.id.yesButton)
        val noButton = view.findViewById<Button>(R.id.noButton)
        changeItemsVisibility(View.GONE)

        yesButton.setOnClickListener {
            // Change view to patrol page if robot at home base
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatrolPage())
                .addToBackStack(null)
                .commit()
        }

        noButton.setOnClickListener {
            RobotController.speak("I need to go to home base to initialize")
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
}