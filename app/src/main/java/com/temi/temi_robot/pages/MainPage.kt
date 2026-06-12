package com.temi.temi_robot.pages

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.temi.temi_robot.detection.WaveGestureRecognizer
import android.annotation.SuppressLint
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
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.telemetry.TelemetryClient
import androidx.core.content.edit

import com.bumptech.glide.Glide
import android.widget.ImageView

// Page to display to ask questions to the robot. It's his main page
class MainPage : Fragment(), RobotController.RequestReadyCallback, RobotController.MeetingStartedCallback, RobotController.BackToBaseCallback, RobotController.TtsStateCallback, RobotController.DestinationReachedCallback {
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Délai de grâce avant le retour base sur perte réseau : un simple changement
    // de Wi-Fi déclenche onLost quelques secondes avant que le nouveau réseau
    // soit prêt, sans que ce soit une vraie panne
    private val lostNetworkHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lostNetworkGraceMillis = 7_000L
    private var waveRecognizer: WaveGestureRecognizer? = null

    // Variables pour l'animation du robot sur le menu
    private val animationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isSmiling = true

    // Variables pour l'animation de lecture
    private var isReadingTalking = false
    private val typeWriterHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // --- VARIABLES MODE VEILLE (SLEEP) ---
    private val inactivityHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepAnimationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isSleeping = false

    private var isWakingUp = false
    private var sleepStep = 0
    private val SLEEP_TIMEOUT = 300000L // Temps avant de s'endormir : 300 000 ms (300 secondes - 5 minutes)

    private var snorePlayer: android.media.MediaPlayer? = null
    private var hihiPlayer: android.media.MediaPlayer? = null
    private var embarrassedStep = 0

    // LA BASE DE DONNÉES DE TOUTES LES CARTES
    private val mapsData = mapOf(
        "R4 Block Complete (USE THIS) for RIG1" to listOf(
            "test point 1", "test point 2", "home base", "tour start spot",
            "r410 front door", "r406", "r412", "r405", "award exit",
            "trophy cabinet 1", "r416", "r417", "r410 back door",
            "middle ramp", "reception", "back entrance"
        ),
        "S118" to listOf(
            "home base", "start", "okura", "festo", "pcb",
            "pca", "abb", "kawada", "tea time"
        ),
        "Library level 4 (pls dont delete)1" to listOf(
            "project reports", "patrol southwing back", "health sciences 2",
            "life sciences collection", "health sciences", "fiction books",
            "design collection", "photocopying stations", "patrol southwing entry",
            "research carrels south", "patrol southwing", "smart space",
            "patrol entrance", "home base", "book recommendations", "smart kiosk",
            "patrol centerwing", "exhibition", "patrol south corridor", "magazines",
            "lifestyle books", "cafe", "patrol south door", "performance stage",
            "lifestyle media", "patrol north corridor", "dvds", "patrol north door",
            "lean for life pod", "patrol northwing", "patrol northwing entry",
            "smart learning hub", "idea space", "patrol northwing2",
            "patrol northwing2 grass", "dream space", "patrol northwing2 middle",
            "think space", "patrol northwing2 back", "management collection",
            "patrol northwing1", "patrol northwing1 middle", "patrol northwing1 back"
        )
    )

    // La liste des points de la carte ACTUELLE (pour l'écoute vocale)
    private var currentActivePoints: List<String> = emptyList()

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
        val view = inflater.inflate(R.layout.layout_main_page, container, false)
        return view
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetLocalInactivityTimer()

        //  Lancer l'animation du robot sur le menu
        animationHandler.post(blinkRunnable)

        // Going to lost connection page when Wi-Fi is disconnected and moving the robot to home base
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)

                lostNetworkHandler.removeCallbacksAndMessages(null)
                lostNetworkHandler.postDelayed({
                    // Un réseau est revenu pendant le délai de grâce
                    // (simple changement de Wi-Fi) : on ne fait rien
                    if (connectivityManager.activeNetwork != null) return@postDelayed
                    if (!isAdded) return@postDelayed

                    // Clôture de la session en télémétrie
                    TelemetryClient.endSession()

                    // Sending temi to home base
                    RobotController.goToHomeBase()

                    // Change view to lost connection page
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, LostConnectionPage())
                        .addToBackStack(null)
                        .commit()
                }, lostNetworkGraceMillis)
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Réseau retrouvé : on annule le retour base en attente
                lostNetworkHandler.removeCallbacksAndMessages(null)
            }
        }

        // Registering callback to detect system Wi-Fi changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Hide top bar
        RobotController.hideTopBar()

        // Set Callback to listen to arrival
        RobotController.setDestinationReachedCallback(this)

        // Set Callback to listen to TTS State for animation
        RobotController.setTtsStateCallback(this)

        // Set Callback to listen to user request event
        RobotController.setRequestReadyCallback(this)

        // Set Callback to listen to meeting started event
        RobotController.setMeetingStartedCallback(this)

        // Set Callback to listen to back to base event
        RobotController.setBackToBaseCallback(this)

        // To know if we got triggered by an alarm or not
        val notPatrolAgain = arguments?.getString("notPatrolAgain")
        // If we got triggered by an alarm we don't need to patrol again (avoiding bugs)
        if(notPatrolAgain != "true"){
            RobotController.patrol()
        }

        // Set last request time now to not trigger detection automatically when going on main page
        RobotController.setLastRequestTimeNow()

        // Buttons
        val interactionButton = view.findViewById<Button>(R.id.interactionButton)
        val settingsButton = view.findViewById<ImageButton>(R.id.settingsButton)
        val timeButton = view.findViewById<ImageButton>(R.id.timeButton)


        val textInput = view.findViewById<android.widget.EditText>(R.id.textInput)
        val sendTextButton = view.findViewById<Button>(R.id.sendTextButton)

        // --- Gestion de l'écran de lecture ---
        val readingOverlay = view.findViewById<android.widget.RelativeLayout>(R.id.readingOverlay)
        val readingTextView = view.findViewById<android.widget.TextView>(R.id.readingTextView)
        val closeReadingButton = view.findViewById<Button>(R.id.closeReadingButton)

        // On regarde si on vient de recevoir une réponse de l'IA
        val answerText = arguments?.getString("answer")
        val startSpeaking = arguments?.getBoolean("startSpeaking") ?: false

        if (answerText != null) {
            // On affiche le panneau et on écrit le texte
            readingOverlay.visibility = View.VISIBLE
            readingTextView.text = answerText

            animateTextTypewriter(readingTextView, answerText)
            // On fait parler le robot UNIQUEMENT maintenant que la page écoute les signaux !
            if (startSpeaking) {
                RobotController.speak(answerText)
                arguments?.putBoolean("startSpeaking", false) // Sécurité pour qu'il ne répète pas
            }

        }else if (savedInstanceState == null) {
            // : S'il n'y a pas de réponse à lire ET que c'est l'ouverture de la page
            RobotController.speak("I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen.")
        }

        // --- LOGIQUE DE L'ÉCRAN D'ARRIVÉE ---
        val arrivedOverlay = view.findViewById<View>(R.id.arrivedOverlay)

        // Bouton 1 : Poser une question (efface juste l'écran)
        view.findViewById<Button>(R.id.btnAskQuestion).setOnClickListener {
            arrivedOverlay.visibility = View.GONE
            resetLocalInactivityTimer()
        }

        // Bouton 2 : Retour Base (ordonne au robot de rentrer)
        view.findViewById<Button>(R.id.btnGoHome).setOnClickListener {
            arrivedOverlay.visibility = View.GONE
            RobotController.speak("Going back to my home base.")
            RobotController.goToHomeBase()
            onBackToBase() // Déclenche l'animation de retour
        }

        // Comportement du bouton "Finish"
        closeReadingButton.setOnClickListener {
            resetLocalInactivityTimer()
            readingOverlay.visibility = View.GONE
            RobotController.stopSpeaking()
            RobotController.resetInactivityTimer()
            typeWriterHandler.removeCallbacksAndMessages(null)
        }


        // Bouton pour envoyer une question tapée au clavier
        sendTextButton.setOnClickListener {
            resetLocalInactivityTimer()
            val question = textInput.text.toString()
            if (question.isNotEmpty()) {
                RobotController.finishConversation()
                textInput.setText("")
                textInput.clearFocus()
                onRequestIsReady(question)
            }
        }

        // --- Réveil Tactile ---
        val sleepOverlay = view.findViewById<View>(R.id.sleepOverlay)
        sleepOverlay.setOnClickListener {
            if (isSleeping) {
                wakeUpSequence()
            }
        }

        // Defining arguments for navigation
        val passwordPage = PasswordPage()
        val args = Bundle()

        // --- Réveil Tactile ---
        val sleepOverlayView = view.findViewById<View>(R.id.sleepOverlay)
        sleepOverlayView.setOnClickListener {
            if (isSleeping) {
                wakeUpSequence()
            }
        }

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
            args.putString("from", "locationsSettings")
            passwordPage.arguments = args
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }

        // Call button behavior (écran d'appel des contacts du robot)
        view.findViewById<ImageButton>(R.id.callButton).setOnClickListener {
            resetLocalInactivityTimer()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CallPage())
                .addToBackStack(null)
                .commit()
        }

        // Time button behavior
        timeButton.setOnClickListener {
            args.putString("from", "timeSettings")
            passwordPage.arguments = args
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }

        // 🔴 --- LOGIQUE DE L'ÉCRAN DE NAVIGATION (MAP) MULTI-CARTES --- 🔴
        val mapMenuButton = view.findViewById<ImageButton>(R.id.mapMenuButton)
        val mapOverlay = view.findViewById<View>(R.id.mapOverlay)
        val closeMapButton = view.findViewById<Button>(R.id.closeMapButton)
        val destinationsGrid = view.findViewById<android.widget.GridLayout>(R.id.destinationsGrid)

        mapMenuButton.setOnClickListener {
            resetLocalInactivityTimer()
            mapOverlay.visibility = View.VISIBLE
        }

        closeMapButton.setOnClickListener {
            resetLocalInactivityTimer()
            mapOverlay.visibility = View.GONE
        }

        // --- Le moteur de création des boutons ---
        fun loadMapAndGenerateButtons(mapName: String) {
            resetLocalInactivityTimer()

            // 1. Ordonner au robot de charger la carte
            RobotController.setMapName(mapName)
            RobotController.loadMap()
            RobotController.speak("Loading map for $mapName.")

            // 2. Récupérer les points de cette carte
            val points = mapsData[mapName] ?: emptyList()
            currentActivePoints = points // Met à jour l'intelligence vocale !

            // 3. Vider l'ancienne grille
            destinationsGrid.removeAllViews()

            // 4. Générer les nouveaux boutons
            val density = resources.displayMetrics.density
            for (point in points) {
                val btn = Button(requireContext())
                btn.text = point.uppercase()
                btn.setTextColor(android.graphics.Color.WHITE)
                btn.textSize = 18f
                btn.isAllCaps = false
                btn.setBackgroundResource(R.drawable.card_bg) // Ton fond gris

                // Tailles des boutons (Largeur: 240dp, Hauteur: 80dp)
                val params = android.widget.GridLayout.LayoutParams()
                params.width = (240 * density).toInt()
                params.height = (80 * density).toInt()
                params.setMargins((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
                btn.layoutParams = params

                // Action quand on clique sur le bouton généré
                btn.setOnClickListener {
                    resetLocalInactivityTimer()
                    mapOverlay.visibility = View.GONE
                    RobotController.speak("Sure, follow me to the $point.")
                    RobotController.goToLocation(point)
                }

                destinationsGrid.addView(btn)
            }
        }

        // Clics sur les cartes (à gauche de l'écran)
        view.findViewById<Button>(R.id.btnMapR4).setOnClickListener {
            loadMapAndGenerateButtons("R4 Block Complete (USE THIS) for RIG1")
        }

        view.findViewById<Button>(R.id.btnMapS118).setOnClickListener {
            loadMapAndGenerateButtons("S118")
        }

        view.findViewById<Button>(R.id.btnMapLibrary).setOnClickListener {
            loadMapAndGenerateButtons("Library level 4 (pls dont delete)1")
        }

        // Par défaut, au démarrage, on charge R4 dans la mémoire UI
        loadMapAndGenerateButtons("R4 Block Complete (USE THIS) for RIG1")
        // --- Wave gesture detection ---
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            initWaveDetector()
        } else {
            Log.w("MainPage", "Camera permission not granted — wave detection unavailable this session")
        }

        // --- Clics sur les 4 grandes cartes (Questions prédéfinies) ---
        view.findViewById<Button>(R.id.cardPool).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady("Where is the swimming pool ?")
        }
        view.findViewById<Button>(R.id.cardLibrary).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady("Where is the library ?")
        }
        view.findViewById<Button>(R.id.cardClubs).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady("Tell me about Student Clubs")
        }
        view.findViewById<Button>(R.id.cardBursary).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady("How do I apply for a bursary ?")
        }
    }

    // INTERCEPTION DES DEMANDES DE DÉPLACEMENT Vocales ou Clavier
    override fun onRequestIsReady(request: String) {
        TelemetryClient.recordConversationTurn(request)
        val lowerReq = request.lowercase()

        // 1. INTERCEPTION POUR LE DÉPLACEMENT
        if (lowerReq.contains("take me to") || lowerReq.contains("go to") || lowerReq.contains("bring me to")) {

            // Cherche uniquement dans les points de la carte actuellement chargée
            for (location in currentActivePoints) {
                if (lowerReq.contains(location.lowercase())) {
                    RobotController.speak("Sure! Please follow me to the $location.")
                    RobotController.goToLocation(location)
                    return
                }
            }

            RobotController.speak("I don't have this location saved on my CURRENT map. Please check the map menu.")
            return
        }

        // 2. COMPORTEMENT NORMAL (IA Python)
        (activity as MainActivity).userRequest = request
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoadingPage())
            .addToBackStack(null)
            .commit()
    }

    override fun onMeetingStarted() {
        val prefs = requireActivity().getSharedPreferences("temi_state", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_fragment", this::class.java.name)
                .putBoolean("should_restore_fragment", true)
        }
    }

    override fun onBackToBase() {
        TelemetryClient.endSession()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, GoToBasePage())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        lostNetworkHandler.removeCallbacksAndMessages(null)
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun initWaveDetector() {
        RobotController.setDetectionModeOn(false, 0.5f)
        waveRecognizer = WaveGestureRecognizer(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
        ) {
            requireActivity().runOnUiThread {
                TelemetryClient.track("wave")
                if (isSleeping) {
                    wakeUpSequence()
                } else {
                    RobotController.speak("Hello!")
                    TelemetryClient.track("greeting")
                    resetLocalInactivityTimer()
                }
            }
        }
        waveRecognizer?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        waveRecognizer?.stop()
        waveRecognizer = null
        RobotController.setDetectionModeOn(true, 0.5f)
        animationHandler.removeCallbacks(blinkRunnable)
        inactivityHandler.removeCallbacksAndMessages(null)
        sleepAnimationHandler.removeCallbacksAndMessages(null)
        stopSnoring()
        hihiPlayer?.release()
        hihiPlayer = null
    }

    private val talkingRunnable = object : Runnable {
        override fun run() {
            val faceImage = view?.findViewById<ImageView>(R.id.robotFace)
            if (faceImage != null && isReadingTalking) {
                if (faceImage.tag == "talking") {
                    faceImage.setImageResource(R.drawable.smiling_temi)
                    faceImage.tag = "smiling"
                } else {
                    faceImage.setImageResource(R.drawable.talking_temi)
                    faceImage.tag = "talking"
                }
                animationHandler.postDelayed(this, 150)
            } else if (faceImage != null) {
                faceImage.setImageResource(R.drawable.smiling_temi)
                faceImage.tag = "smiling"
            }
        }
    }

    override fun onTtsStart() {
        activity?.runOnUiThread {
            isReadingTalking = true
            animationHandler.post(talkingRunnable)
        }
    }

    override fun onTtsStop() {
        activity?.runOnUiThread {
            isReadingTalking = false
        }
    }

    private fun animateTextTypewriter(textView: android.widget.TextView, fullText: String) {
        textView.text = ""
        var charIndex = 0
        val scrollView = textView.parent as? android.widget.ScrollView
        val runnable = object : Runnable {
            override fun run() {
                if (charIndex < fullText.length) {
                    textView.append(fullText[charIndex].toString())
                    charIndex++
                    scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                    typeWriterHandler.postDelayed(this, 60)
                }
            }
        }
        typeWriterHandler.post(runnable)
    }

    private fun resetLocalInactivityTimer() {
        inactivityHandler.removeCallbacksAndMessages(null)
        if (!isSleeping && view?.findViewById<View>(R.id.readingOverlay)?.visibility != View.VISIBLE) {
            inactivityHandler.postDelayed(goToSleepRunnable, SLEEP_TIMEOUT)
        }
    }

    private val goToSleepRunnable = Runnable {
        TelemetryClient.endSession()
        TelemetryClient.track("idle")
        isSleeping = true
        view?.findViewById<View>(R.id.sleepOverlay)?.visibility = View.VISIBLE
        sleepAnimationHandler.post(sleepAnimationRunnable)
        startSnoring()
    }

    private val sleepAnimationRunnable = object : Runnable {
        override fun run() {
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            if (sleepImage != null && isSleeping) {
                when (sleepStep) {
                    0 -> sleepImage.setImageResource(R.drawable.sleeping_temi)
                    1 -> sleepImage.setImageResource(R.drawable.sleeping_temi_z)
                    2 -> sleepImage.setImageResource(R.drawable.sleeping_temi_zz)
                    3 -> sleepImage.setImageResource(R.drawable.sleeping_temi_zzz)
                }
                sleepStep = (sleepStep + 1) % 4
                sleepAnimationHandler.postDelayed(this, 500)
            }
        }
    }

    private val embarrassedRunnable = object : Runnable {
        override fun run() {
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            if (sleepImage != null) {
                if (embarrassedStep % 2 == 0) {
                    sleepImage.setImageResource(R.drawable.embarassed_temi)
                } else {
                    sleepImage.setImageResource(R.drawable.embarassed2_temi)
                }
                embarrassedStep++
                sleepAnimationHandler.postDelayed(this, 200)
            }
        }
    }

    private fun wakeUpSequence() {
        if (isWakingUp) return
        isWakingUp = true
        sleepAnimationHandler.removeCallbacksAndMessages(null)
        stopSnoring()
        playHihi()
        embarrassedStep = 0
        sleepAnimationHandler.post(embarrassedRunnable)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sleepAnimationHandler.removeCallbacks(embarrassedRunnable)
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            sleepImage?.setImageResource(R.drawable.smiling_temi)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                view?.findViewById<View>(R.id.sleepOverlay)?.visibility = View.GONE
                isSleeping = false
                isWakingUp = false
                resetLocalInactivityTimer()
                RobotController.speak("I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen.")
            }, 1000)
        }, 5000)
    }

    private fun startSnoring() {
        context?.let {
            snorePlayer = android.media.MediaPlayer.create(it, R.raw.snoring)
            snorePlayer?.isLooping = false
            snorePlayer?.start()
            sleepAnimationHandler.postDelayed({ stopSnoring() }, 5000)
        }
    }

    private fun stopSnoring() {
        snorePlayer?.stop()
        snorePlayer?.release()
        snorePlayer = null
    }

    private fun playHihi() {
        context?.let {
            hihiPlayer = android.media.MediaPlayer.create(it, R.raw.hihi)
            hihiPlayer?.start()
            hihiPlayer?.setOnCompletionListener { player ->
                player.release()
                hihiPlayer = null
            }
        }
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            val robotImage = view?.findViewById<android.widget.ImageView>(R.id.menuRobotCharacter)
            if (robotImage != null) {
                if (isSmiling) {
                    robotImage.setImageResource(R.drawable.robot_grimace)
                    isSmiling = false
                    animationHandler.postDelayed(this, 500)
                } else {
                    robotImage.setImageResource(R.drawable.robot_smile)
                    isSmiling = true
                    animationHandler.postDelayed(this, 4000)
                }
            }
        }
    }

    override fun onDestinationReached() {
        activity?.runOnUiThread {
            view?.findViewById<View>(R.id.arrivedOverlay)?.visibility = View.VISIBLE
            RobotController.speak("We arrived. What would you like to do next?")
            resetLocalInactivityTimer()
        }
    }
}