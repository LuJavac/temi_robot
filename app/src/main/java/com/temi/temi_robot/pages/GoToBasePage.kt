package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController

class GoToBasePage : Fragment(), RobotController.BackToPatrolCallback {
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

        // Set Callback to listen to back to patrol page event
        RobotController.setBackToPatrolCallback(this)

        // Nyp logo on patrol interface and red button to write the restart message
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Adding button just for decoration in that case
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        interactionButton.setOnClickListener{
        }
    }

    override fun onBackToPatrol() {
        // Change view to patrol page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PatrolPage())
            .addToBackStack(null)
            .commit()
    }

}