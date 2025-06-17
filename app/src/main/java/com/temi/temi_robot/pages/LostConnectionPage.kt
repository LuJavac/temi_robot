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
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R

class LostConnectionPage : Fragment(){

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
        // View layout
        val view = inflater.inflate(R.layout.layout_lost_connection, container, false)

        // Adding network callback to detect system Wi-FI deconnections
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Change view back to patrol page
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PatrolPage())
                    .addToBackStack(null)
                    .commit()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)


        // Nyp logo on patrol interface and red button to write the restart message
        val nypLogo = view.findViewById<ImageView>(R.id.nypLogo)

        val interactionButton = view.findViewById<Button>(R.id.interactionButton)

        interactionButton.setOnClickListener{

        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }


}