package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.robotemi.sdk.telepresence.CallState
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.ui_utils.CallHangupOverlay

// Page d'appel : liste les contacts du robot et lance un appel vidéo
// (startMeeting). Le raccrochage côté robot se fait via la bulle flottante
// CallHangupOverlay, affichée par-dessus l'écran d'appel natif du Temi.
class CallPage : Fragment() {

    private var statusText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_call_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Stop movement while on call page
        RobotController.stopMovement()
        RobotController.setBlockMode(true)
        RobotController.hideTopBar()

        statusText = view.findViewById(R.id.callStatusText)
        val contactsContainer = view.findViewById<LinearLayout>(R.id.contactsContainer)

        // Permission de superposition pour la bulle raccrocher, demandée une
        // seule fois. Si elle est refusée, les appels marchent sans la bulle.
        val prefs = requireContext().getSharedPreferences("call_page", Context.MODE_PRIVATE)
        if (!CallHangupOverlay.canDraw(requireContext()) &&
            !prefs.getBoolean("overlayPermissionAsked", false)
        ) {
            prefs.edit().putBoolean("overlayPermissionAsked", true).apply()
            showStatus("Please grant the 'display over other apps' permission")
            CallHangupOverlay.requestPermission(requireContext())
        }

        // Les états d'appel arrivent ici tant que la page est ouverte
        RobotController.setCallStateCallback { state ->
            activity?.runOnUiThread { onCallStateChanged(state) }
        }

        val contacts = RobotController.getCallableContacts()
        if (contacts.isEmpty()) {
            showStatus("No contact available")
            RobotController.speak("Sorry, there is nobody I can call at the moment.")
        }
        for (contact in contacts) {
            val button = Button(requireContext())
            button.text = contact.name
            button.textSize = 22f
            button.setTextColor(android.graphics.Color.WHITE)
            button.setBackgroundResource(R.drawable.card_bg)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 180
            )
            params.setMargins(0, 10, 0, 10)
            button.layoutParams = params
            button.setOnClickListener {
                RobotController.startCall(contact)
                showStatus("Calling ${contact.name}...")
                // Bouton raccrocher visible par-dessus l'écran d'appel natif
                // (no-op si la permission de superposition n'est pas accordée)
                CallHangupOverlay.show(requireContext()) {
                    RobotController.stopCall()
                    context?.let { ctx -> CallHangupOverlay.hide(ctx) }
                }
            }
            contactsContainer.addView(button)
        }

        view.findViewById<Button>(R.id.backButton).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainPage())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun onCallStateChanged(state: CallState.State) {
        when (state) {
            CallState.State.INITIALIZED -> showStatus("Calling...")
            CallState.State.STARTED -> showStatus("Call in progress")
            CallState.State.ENDED -> {
                showStatus("Call ended")
                context?.let { CallHangupOverlay.hide(it) }
            }
            CallState.State.DECLINED -> {
                showStatus("The call was declined")
                RobotController.speak("Sorry, the call was declined.")
                context?.let { CallHangupOverlay.hide(it) }
            }
            CallState.State.NOT_ANSWERED -> {
                showStatus("No answer")
                RobotController.speak("Sorry, nobody answered the call.")
                context?.let { CallHangupOverlay.hide(it) }
            }
            CallState.State.BUSY -> {
                showStatus("The contact is busy")
                RobotController.speak("Sorry, this person is busy right now.")
                context?.let { CallHangupOverlay.hide(it) }
            }
            CallState.State.POOR_CONNECTION, CallState.State.CANT_JOIN -> {
                showStatus("Connection problem, please try again")
                RobotController.speak("Sorry, I have a connection problem. Please try again.")
                context?.let { CallHangupOverlay.hide(it) }
            }
        }
    }

    private fun showStatus(message: String) {
        statusText?.text = message
        statusText?.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        RobotController.setCallStateCallback(null)
        context?.let { CallHangupOverlay.hide(it) }
        statusText = null
    }
}
