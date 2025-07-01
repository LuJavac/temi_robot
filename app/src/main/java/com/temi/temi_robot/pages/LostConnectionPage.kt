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
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R

// Class for lost connection page when Wi-Fi is disconnected
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

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Going back to patrol page when Wi-Fi is reconnected
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Change view back to main page
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MainPage())
                    .addToBackStack(null)
                    .commit()
            }
        }

        // Registering callback to detect system Wi-FI changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Adding button just for decoration in that case
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        interactionButton.setOnClickListener{

        }
    }

    // Unregistering callback to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }


}