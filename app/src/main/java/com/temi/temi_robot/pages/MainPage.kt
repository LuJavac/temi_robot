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
import androidx.core.content.edit

import com.bumptech.glide.Glide
import android.widget.ImageView

// Page to display to ask questions to the robot. It's his main page
class MainPage : Fragment(), RobotController.RequestReadyCallback, RobotController.MeetingStartedCallback, RobotController.BackToBaseCallback, RobotController.TtsStateCallback {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
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


                // Sending temi to home base
                RobotController.goToHomeBase()

                // Change view to lost connection page
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LostConnectionPage())
                    .addToBackStack(null)
                    .commit()
            }

        }

        // Registering callback to detect system Wi-Fi changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)


        // Hide top bar
        RobotController.hideTopBar()

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
            // (Le savedInstanceState == null évite qu'il répète la phrase si on fait juste un aller-retour dans les Paramètres)
            RobotController.speak("I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen.")
        }

        // Comportement du bouton "Finish"
        closeReadingButton.setOnClickListener {

            resetLocalInactivityTimer()

            // 1. On cache l'écran de lecture
            readingOverlay.visibility = View.GONE

            // 2. On coupe la parole au robot s'il n'avait pas fini
            RobotController.stopSpeaking()

            // 3. On remet les compteurs à zéro pour la prochaine question
            RobotController.resetInactivityTimer()
            typeWriterHandler.removeCallbacksAndMessages(null)
        }


        // Bouton pour envoyer une question tapée au clavier
        sendTextButton.setOnClickListener {

            resetLocalInactivityTimer()

            val question = textInput.text.toString()
            if (question.isNotEmpty()) {

                // 1. Coupe l'écoute vocale
                RobotController.finishConversation()

                // 2. Vide le texte IMMÉDIATEMENT (et referme le clavier visuel d'Android)
                textInput.setText("")
                textInput.clearFocus()

                // 3. Envoie au serveur
                onRequestIsReady(question)
            }
        }

        // --- Réveil Tactile ---
        val sleepOverlay = view.findViewById<View>(R.id.sleepOverlay)
        sleepOverlay.setOnClickListener {
            if (isSleeping) {
                // Si l'utilisateur touche l'écran pendant que le robot dort, on le réveille
                wakeUpSequence()
            }
        }

        if(RobotController.isAtHomeBase()){
            interactionButton.text = "Click on the button to ask me something\nor type your question below"
        }

        // Defining arguments for navigation
        val passwordPage = PasswordPage()
        val args = Bundle()

        // --- Réveil Tactile ---
        val sleepOverlayView = view.findViewById<View>(R.id.sleepOverlay)
        sleepOverlayView.setOnClickListener {
            if (isSleeping) {
                // L'utilisateur a tapé sur l'écran noir
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
            // Passing argument for password page to know where we come from
            args.putString("from", "locationsSettings")
            passwordPage.arguments = args

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }

        // Time button behavior
        timeButton.setOnClickListener {

            // Passing argument for password page to know where we come from
            args.putString("from", "timeSettings")
            passwordPage.arguments = args

            // Change view to settings page
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, passwordPage)
                .addToBackStack(null)
                .commit()
        }
        // --- Wave gesture detection ---
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            initWaveDetector()
        } else {
            Log.w("MainPage", "Camera permission not granted — wave detection unavailable this session")
        }

    }

    // When user request arrived, change view to loading page
    override fun onRequestIsReady(request: String) {
        (activity as MainActivity).userRequest = request
        // Change view to loading page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoadingPage())
            .addToBackStack(null)
            .commit()
    }

    // When meeting started, save last page before leaving for call
    override fun onMeetingStarted() {
        // Save last page before leaving for call
        val prefs = requireActivity().getSharedPreferences("temi_state", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_fragment", this::class.java.name)
                .putBoolean("should_restore_fragment", true)
        }
    }

    // Callback to go back to base page when needed
    override fun onBackToBase() {
        // Change view to go back base page
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, GoToBasePage())
            .addToBackStack(null)
            .commit()
    }

    // Unregistering callback to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
    private fun initWaveDetector() {
        RobotController.setDetectionModeOn(false, 0.5f)

        waveRecognizer = WaveGestureRecognizer(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
        ) {
            requireActivity().runOnUiThread {

                if (isSleeping) {
                    // S'il dort, le wave le réveille
                    try {
                        wakeUpSequence()
                    } catch (e: Exception) {
                        TODO("Not yet implemented")
                    }
                } else {
                    // Comportement normal s'il est déjà réveillé
                    RobotController.speak("Hello!")
                    showHelloOverlay()
                    resetLocalInactivityTimer() // Réinitialise le chrono
                }
            }
        }
        waveRecognizer?.start()
    }

    private val helloHideRunnable = Runnable {
        view?.findViewById<View>(R.id.helloOverlay)?.visibility = View.GONE
    }

    private fun showHelloOverlay() {
        val overlay = view?.findViewById<View>(R.id.helloOverlay) ?: return
        overlay.visibility = View.VISIBLE
        overlay.removeCallbacks(helloHideRunnable)
        overlay.postDelayed(helloHideRunnable, 2000L)
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

    // --- GESTION DU VISAGE ANIMÉ PENDANT LA LECTURE ---
    private val talkingRunnable = object : Runnable {
        override fun run() {
            val faceImage = view?.findViewById<ImageView>(R.id.robotFace)
            if (faceImage != null && isReadingTalking) {
                // On alterne rapidement (illusion de parole)
                if (faceImage.tag == "talking") {
                    faceImage.setImageResource(R.drawable.smiling_temi)
                    faceImage.tag = "smiling"
                } else {
                    faceImage.setImageResource(R.drawable.talking_temi)
                    faceImage.tag = "talking"
                }
                // Vitesse d'ouverture/fermeture de la bouche (150ms)
                animationHandler.postDelayed(this, 150)
            } else if (faceImage != null) {
                // S'il s'arrête de parler, on force le sourire
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

    // --- EFFET MACHINE À ÉCRIRE ---
    private fun animateTextTypewriter(textView: android.widget.TextView, fullText: String) {
        textView.text = ""
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (charIndex < fullText.length) {
                    textView.append(fullText[charIndex].toString())
                    charIndex++
                    // Vitesse de frappe (20ms entre chaque lettre)
                    typeWriterHandler.postDelayed(this, 20)
                }
            }
        }
        typeWriterHandler.post(runnable)
    }

    // --- LOGIQUE DU MODE VEILLE ---
    private fun resetLocalInactivityTimer() {
        inactivityHandler.removeCallbacksAndMessages(null)
        // On ne lance le chrono de sommeil que si on n'est pas déjà en train de lire une réponse
        if (!isSleeping && view?.findViewById<View>(R.id.readingOverlay)?.visibility != View.VISIBLE) {
            inactivityHandler.postDelayed(goToSleepRunnable, SLEEP_TIMEOUT)
        }
    }

    private val goToSleepRunnable = Runnable {
        isSleeping = true
        view?.findViewById<View>(R.id.sleepOverlay)?.visibility = View.VISIBLE
        sleepAnimationHandler.post(sleepAnimationRunnable)
        startSnoring()
    }

    private val sleepAnimationRunnable = object : Runnable {
        override fun run() {
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            if (sleepImage != null && isSleeping) {
                // Animation Zzz...
                when (sleepStep) {
                    0 -> sleepImage.setImageResource(R.drawable.sleeping_temi)
                    1 -> sleepImage.setImageResource(R.drawable.sleeping_temi_z)
                    2 -> sleepImage.setImageResource(R.drawable.sleeping_temi_zz)
                    3 -> sleepImage.setImageResource(R.drawable.sleeping_temi_zzz)
                }
                sleepStep = (sleepStep + 1) % 4
                sleepAnimationHandler.postDelayed(this, 500) // Vitesse de l'animation Zzz (0.5s)
            }
        }
    }

    // --- ANIMATION DE RÉVEIL (GÊNÉ) ---
    private val embarrassedRunnable = object : Runnable {
        override fun run() {
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            if (sleepImage != null) {
                // Alterne entre les deux images de gêne
                if (embarrassedStep % 2 == 0) {
                    sleepImage.setImageResource(R.drawable.embarassed_temi)
                } else {
                    sleepImage.setImageResource(R.drawable.embarassed2_temi)
                }
                embarrassedStep++

                // Vitesse du clignotement de panique (200 millisecondes)
                sleepAnimationHandler.postDelayed(this, 200)
            }
        }
    }

    private fun wakeUpSequence() {
        if (isWakingUp) return
        isWakingUp = true

        // 1. On coupe l'animation des Zzz ET on arrête le ronflement
        sleepAnimationHandler.removeCallbacksAndMessages(null)
        stopSnoring()

        // 2. On lance le petit rire de gêne ("hihi")
        playHihi()

        // 3. On lance l'animation paniquée/gênée
        embarrassedStep = 0
        sleepAnimationHandler.post(embarrassedRunnable)

        // 4. On laisse l'animation gênée pendant 2.5 secondes
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({

            // On arrête de clignoter la gêne
            sleepAnimationHandler.removeCallbacks(embarrassedRunnable)

            // 5. On remet le grand sourire classique pour reprendre le travail
            val sleepImage = view?.findViewById<ImageView>(R.id.sleepRobotImage)
            sleepImage?.setImageResource(R.drawable.smiling_temi)

            // 6. On attend encore 1 seconde avant d'enlever l'écran noir pour voir le sourire
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                view?.findViewById<View>(R.id.sleepOverlay)?.visibility = View.GONE
                isSleeping = false
                isWakingUp = false
                resetLocalInactivityTimer() // On relance le chrono d'inactivité

                RobotController.speak("I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen.")
            }, 1000)

        }, 5000) // L'animation gênée dure 5 secondes
    }

    // --- LECTURE DES SONS ---
    private fun startSnoring() {
        context?.let {
            snorePlayer = android.media.MediaPlayer.create(it, R.raw.snoring)
            snorePlayer?.isLooping = false // Le ronflement tourne en boucle !
            snorePlayer?.start()

            sleepAnimationHandler.postDelayed({
                stopSnoring()
            }, 5000)
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
                player.release() // On nettoie la mémoire une fois le rire fini
                hihiPlayer = null
            }
        }
    }

    // --- ANIMATION DU ROBOT SUR LE MENU ---
    private val blinkRunnable = object : Runnable {
        override fun run() {
            val robotImage = view?.findViewById<android.widget.ImageView>(R.id.menuRobotCharacter)
            if (robotImage != null) {
                if (isSmiling) {
                    // On met la grimace
                    robotImage.setImageResource(R.drawable.robot_grimace)
                    isSmiling = false
                    animationHandler.postDelayed(this, 500) // Reste en grimace 0.5 seconde
                } else {
                    // On remet le sourire
                    robotImage.setImageResource(R.drawable.robot_smile)
                    isSmiling = true
                    animationHandler.postDelayed(this, 4000) // Reste souriant 4 secondes
                }
            }
        }
    }

}