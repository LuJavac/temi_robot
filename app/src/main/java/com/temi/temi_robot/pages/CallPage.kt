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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.robotemi.sdk.UserInfo
import com.robotemi.sdk.telepresence.CallState
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.telemetry.TelemetryClient
import com.temi.temi_robot.ui_utils.CallHangupOverlay

// Page d'appel : liste les contacts du robot et lance un appel vidéo via
// RobotController.startCall (startTelepresence ; startMeeting → CANT_JOIN sur
// ce robot). Le raccrochage côté robot se fait via la bulle flottante
// CallHangupOverlay, affichée par-dessus l'écran d'appel natif du Temi.
class CallPage : Fragment() {

    private var statusText: TextView? = null

    // Si personne ne décroche au bout de ce délai, on annule la tentative
    // d'appel nous-mêmes : sinon le robot reste en état "occupé" sur l'écran
    // d'appel natif et bloque les appels suivants
    private val ringTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ringTimeoutMillis = 30_000L

    // Fermeture automatique de la page quelques secondes après la fin de
    // l'appel (terminé, refusé, sans réponse...), pour ne pas rester bloqué
    // sur cette page si la personne s'en va
    private val autoCloseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoCloseDelayMillis = 5_000L

    // Juste après un déplacement, le robot vient souvent de changer de borne
    // Wi-Fi : la connexion n'est pas encore rétablie quand on arrive au point.
    // On attend donc que le réseau soit disponible avant de composer l'appel
    // (sinon startTelepresence échoue en CANT_JOIN -> "connection problem").
    private val connectWaitHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val connectWaitTimeoutMillis = 12_000L
    private var pendingNetworkCallback: ConnectivityManager.NetworkCallback? = null

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

        // Signal fiable de fin d'appel : se déclenche aussi quand
        // l'interlocuteur distant raccroche/quitte. On referme alors tout de
        // suite l'écran d'appel natif du Temi (sinon il reste affiché avec sa
        // détection "personne dans la salle" et l'appel ne se ferme pas seul).
        RobotController.setCallEndedCallback {
            activity?.runOnUiThread { onCallEnded() }
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
                // Nouvel appel : on annule une éventuelle fermeture en attente
                autoCloseHandler.removeCallbacksAndMessages(null)
                startCallWhenOnline(contact)
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

    // Lance l'appel en s'assurant d'abord que le réseau est prêt. On coupe le
    // TTS d'arrivée ("We arrived...") puis : si le réseau est déjà disponible on
    // compose tout de suite, sinon on attend qu'il revienne (cas du Wi-Fi qui se
    // rétablit après un déplacement) avant de composer, au lieu d'appeler dans le
    // vide et de tomber sur "connection problem".
    private fun startCallWhenOnline(contact: UserInfo) {
        RobotController.stopSpeaking()
        if (isNetworkAvailable()) {
            placeCall(contact)
            return
        }
        showStatus("Connecting...")
        waitForNetworkThenCall(contact)
    }

    private fun isNetworkAvailable(): Boolean {
        // Pas de ConnectivityManager (hors MainActivity) : on n'empêche pas l'appel
        val cm = (activity as? MainActivity)?.connectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun waitForNetworkThenCall(contact: UserInfo) {
        val cm = (activity as? MainActivity)?.connectivityManager
        if (cm == null) {
            placeCall(contact)
            return
        }
        // Garde-fou : si le réseau ne revient pas, on abandonne proprement
        val timeoutRunnable = Runnable {
            cancelNetworkWait()
            showStatus("Connection problem, please try again")
            RobotController.speak("Sorry, I have a connection problem. Please try again.")
            scheduleAutoClose()
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    connectWaitHandler.removeCallbacks(timeoutRunnable)
                    cancelNetworkWait()
                    placeCall(contact)
                }
            }
        }
        pendingNetworkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        connectWaitHandler.postDelayed(timeoutRunnable, connectWaitTimeoutMillis)
    }

    private fun cancelNetworkWait() {
        connectWaitHandler.removeCallbacksAndMessages(null)
        val cb = pendingNetworkCallback ?: return
        pendingNetworkCallback = null
        (activity as? MainActivity)?.connectivityManager?.let { cm ->
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
    }

    // Compose réellement l'appel : startTelepresence + bulle raccrocher + timeout
    // de sonnerie. Appelé seulement quand le réseau est prêt (cf. startCallWhenOnline),
    // donc potentiellement de façon différée -> on revérifie que la page est attachée.
    private fun placeCall(contact: UserInfo) {
        if (!isAdded) return
        RobotController.startCall(contact)
        TelemetryClient.track("prof_call")
        showStatus("Calling ${contact.name}...")
        // Bouton raccrocher visible par-dessus l'écran d'appel natif
        // (no-op si la permission de superposition n'est pas accordée).
        // Il raccroche ET ferme la page immédiatement : le bouton rouge
        // natif ne raccroche pas directement (compteur d'une minute)
        CallHangupOverlay.show(requireContext()) {
            ringTimeoutHandler.removeCallbacksAndMessages(null)
            autoCloseHandler.removeCallbacksAndMessages(null)
            RobotController.stopCall()
            context?.let { ctx -> CallHangupOverlay.hide(ctx) }
            closePageNow()
        }
        // Annulation automatique si personne ne décroche
        ringTimeoutHandler.removeCallbacksAndMessages(null)
        ringTimeoutHandler.postDelayed({
            RobotController.stopCall()
            context?.let { ctx -> CallHangupOverlay.hide(ctx) }
            showStatus("No answer, call cancelled")
            RobotController.speak("Sorry, nobody answered the call.")
            scheduleAutoClose()
        }, ringTimeoutMillis)
    }

    private fun onCallStateChanged(state: CallState.State) {
        // L'appel a décroché ou s'est terminé de lui-même :
        // plus besoin du timeout de sonnerie
        if (state != CallState.State.INITIALIZED) {
            ringTimeoutHandler.removeCallbacksAndMessages(null)
        }
        // Fin d'appel, quelle qu'en soit la raison : on referme l'écran natif
        // du Temi et la page se ferme seule
        if (state != CallState.State.INITIALIZED && state != CallState.State.STARTED) {
            endNativeCall()
            scheduleAutoClose()
        }
        when (state) {
            CallState.State.INITIALIZED -> showStatus("Calling...")
            CallState.State.STARTED -> showStatus("Call in progress")
            CallState.State.ENDED -> showStatus("Call ended")
            CallState.State.DECLINED -> {
                showStatus("The call was declined")
                RobotController.speak("Sorry, the call was declined.")
            }
            CallState.State.NOT_ANSWERED -> {
                showStatus("No answer")
                RobotController.speak("Sorry, nobody answered the call.")
            }
            CallState.State.BUSY -> {
                showStatus("The contact is busy")
                RobotController.speak("Sorry, this person is busy right now.")
            }
            CallState.State.POOR_CONNECTION, CallState.State.CANT_JOIN -> {
                showStatus("Connection problem, please try again")
                RobotController.speak("Sorry, I have a connection problem. Please try again.")
            }
        }
    }

    // Fin d'appel détectée via le signal d'événement (notamment quand
    // l'interlocuteur distant quitte) : même traitement que CallState.ENDED.
    private fun onCallEnded() {
        ringTimeoutHandler.removeCallbacksAndMessages(null)
        showStatus("Call ended")
        endNativeCall()
        scheduleAutoClose()
    }

    // Coupe la session de téléprésence native : ça referme l'écran d'appel du
    // Temi et redonne la main à notre app (même mécanisme éprouvé que le bouton
    // "Hang up"). On ne ramène PAS l'app au premier plan manuellement : le faire
    // pendant un appel encore actif fait apparaître la bulle flottante "retour à
    // l'appel" du Temi en bas de l'écran.
    private fun endNativeCall() {
        RobotController.stopCall()
        context?.let { CallHangupOverlay.hide(it) }
    }

    private fun showStatus(message: String) {
        statusText?.text = message
        statusText?.visibility = View.VISIBLE
    }

    private fun scheduleAutoClose() {
        autoCloseHandler.removeCallbacksAndMessages(null)
        autoCloseHandler.postDelayed({ closePageNow() }, autoCloseDelayMillis)
    }

    // commitAllowingStateLoss : on peut être appelé pendant que l'activité est
    // en arrière-plan derrière l'écran d'appel natif, où commit() planterait
    private fun closePageNow() {
        if (!isAdded) return
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainPage())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ringTimeoutHandler.removeCallbacksAndMessages(null)
        autoCloseHandler.removeCallbacksAndMessages(null)
        cancelNetworkWait()
        RobotController.setCallStateCallback(null)
        RobotController.setCallEndedCallback(null)
        context?.let { CallHangupOverlay.hide(it) }
        statusText = null
    }
}
