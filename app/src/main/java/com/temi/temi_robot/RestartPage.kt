package com.temi.temi_robot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment

class RestartPage : Fragment(){

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_restart, container, false)

        // Nyp logo on patrol interface
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        // Buttons
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)

        // User button behavior
        interactionButton.setOnClickListener{

        }

        return view
    }

}