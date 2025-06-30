package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R

// Class for restart page when robot needs to restart after granting new permissions
class RestartPage : Fragment(){

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // View layout
        val view = inflater.inflate(R.layout.layout_restart, container, false)

        // Adding button just for decoration in that case
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        interactionButton.setOnClickListener{

        }
        return view
    }

}