package com.temi.temi_robot.pages

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.temi.temi_robot.detection.CameraFrameProvider
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.face.FaceClient
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
    private var cameraFrameProvider: CameraFrameProvider? = null

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

    // Points du bloc R4 : identiques sur les deux robots (même bâtiment).
    // Seule la home base diffère, d'où deux cartes distinctes dans Temi Center
    // ("...for RIG1" et "...for BOA1") — on charge la bonne selon le robot.
    // Lieux nommes d'abord, puis les salles par numero croissant.
    // L'ordre de cette liste est l'ordre d'affichage des boutons dans la grille.
    private val r4Points = listOf(
        "reception", "back entrance", "middle ramp", "toilet",
        "award exit", "trophy cabinet 1", "tour start spot",
        "home base", "test point 1", "test point 2",
        "r403", "r404", "r405", "r406",
        "r410 front door", "r410 back door", "r411", "r412",
        "r413", "r414", "r415", "r416", "r417",
        "r420", "r421", "r422", "r423", "r424",
        "r425", "r426", "r427", "r428", "r429"
    )

    // LA BASE DE DONNÉES DE TOUTES LES CARTES
    private val mapsData = mapOf(
        "R4 Block Complete (USE THIS) for RIG1" to r4Points,
        "R4 Block Complete (USE THIS) for BOA1" to r4Points,
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
    // --- DICTIONNAIRE DE TRADUCTION COMPLET ---
    private val uiTexts = mapOf(
        "EN" to mapOf("greeting" to "Hi there, it's Temi! How can I help you?", "pool" to "Where is the swimming pool?", "lib" to "Where is the library?", "club" to "Tell me about Student Clubs", "bursary" to "How do I apply for a bursary?", "hint" to "Type your question here...", "interaction" to "Press here to ask me a question", "selfie" to "📸 Take a selfie with me!", "mapBtn" to "🗺️ Show Interactive Map", "send" to "SEND"),
        "FR" to mapOf("greeting" to "Bonjour, c'est Temi ! Comment puis-je vous aider ?", "pool" to "Où est la piscine ?", "lib" to "Où est la bibliothèque ?", "club" to "Parlez-moi des clubs étudiants", "bursary" to "Comment demander une bourse ?", "hint" to "Tapez votre question...", "interaction" to "Appuyez pour me poser une question", "selfie" to "📸 Prenez un selfie avec moi !", "mapBtn" to "🗺️ Carte Interactive", "send" to "ENVOYER"),
        "ZH" to mapOf("greeting" to "你好，我是 Temi！我能帮到你什么？", "pool" to "游泳池在哪里？", "lib" to "图书馆在哪里？", "club" to "告诉我关于学生社团的事", "bursary" to "如何申请助学金？", "hint" to "在这里输入你的问题...", "interaction" to "点击这里向我提问", "selfie" to "📸 和我一起自拍！", "mapBtn" to "🗺️ 互动地图", "send" to "发送"),
        "MS" to mapOf("greeting" to "Hai, saya Temi! Bagaimana saya boleh bantu anda?", "pool" to "Di manakah kolam renang?", "lib" to "Di manakah perpustakaan?", "club" to "Beritahu saya tentang kelab pelajar", "bursary" to "Bagaimana untuk memohon biasiswa?", "hint" to "Taip soalan anda di sini...", "interaction" to "Tekan di sini untuk bertanya", "selfie" to "📸 Ambil swafoto bersama saya!", "mapBtn" to "🗺️ Peta Interaktif", "send" to "HANTAR"),
        "JA" to mapOf("greeting" to "こんにちは、Temiです！何かお手伝いしましょうか？", "pool" to "プールはどこですか？", "lib" to "図書館はどこですか？", "club" to "学生クラブについて教えて", "bursary" to "奨学金の申請方法は？", "hint" to "ここに質問を入力...", "interaction" to "ここを押して質問してください", "selfie" to "📸 一緒に自撮りしましょう！", "mapBtn" to "🗺️ インタラクティブマップ", "send" to "送信"),
        "DE" to mapOf("greeting" to "Hallo, ich bin Temi! Wie kann ich dir helfen?", "pool" to "Wo ist das Schwimmbad?", "lib" to "Wo ist die Bibliothek?", "club" to "Erzähl mir von Studentenclubs", "bursary" to "Wie beantrage ich ein Stipendium?", "hint" to "Tippen Sie hier Ihre Frage...", "interaction" to "Hier drücken, um zu fragen", "selfie" to "📸 Mach ein Selfie mit mir!", "mapBtn" to "🗺️ Interaktive Karte", "send" to "SENDEN")
    )

    // Noms courts affichés sur les boutons de cartes (sinon on afficherait le nom brut).
    private val mapDisplayNames = mapOf(
        "R4 Block Complete (USE THIS) for RIG1" to "Block R4 (RIG1)",
        "R4 Block Complete (USE THIS) for BOA1" to "Block R4 (BOA1)",
        "S118" to "S118",
        "Library level 4 (pls dont delete)1" to "Library Level 4"
    )

    // La liste des points de la carte ACTUELLE (pour l'écoute vocale)
    private var currentActivePoints: List<String> = emptyList()


    companion object {
        // Phrase d'accueil dite une seule fois par lancement de l'app
        private var hasSpokenIntro = false
    }

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

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetLocalInactivityTimer()

        // Fermer le clavier quand on touche en dehors du champ de saisie :
        // évite de rester coincé avec le clavier ouvert
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val focused = activity?.currentFocus
                if (focused is EditText) {
                    val outRect = Rect()
                    focused.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        focused.clearFocus()
                        hideKeyboard(focused)
                    }
                }
            }
            false
        }

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

        }else if (savedInstanceState == null && !hasSpokenIntro) {
            // Phrase d'accueil dite une seule fois par lancement de l'app : la page
            // principale est recréée à chaque navigation, sans ce flag le robot la
            // répétait à chaque retour
            hasSpokenIntro = true
            RobotController.speak("I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen.")
        }

        // --- LOGIQUE DE L'ÉCRAN D'ARRIVÉE ---
        val arrivedOverlay = view.findViewById<View>(R.id.arrivedOverlay)

        // Bouton 1 : Poser une question (efface juste l'écran)
        view.findViewById<Button>(R.id.btnAskQuestion).setOnClickListener {
            arrivedOverlay.visibility = View.GONE
            resetLocalInactivityTimer()
        }

        // Bouton : Appeler un contact (ouvre la page des appels)
        view.findViewById<Button>(R.id.btnCallContact).setOnClickListener {
            arrivedOverlay.visibility = View.GONE
            resetLocalInactivityTimer()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CallPage())
                .addToBackStack(null)
                .commit()
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

            // Fait parler le robot dans la langue choisie
            val spokenGreeting = uiTexts[RobotController.currentLangCode]?.get("greeting") ?: "How can I help you?"
            RobotController.askQuestion(spokenGreeting)
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

        // --- Le moteur de création des boutons de destinations ---
        fun generateDestinationButtons(points: List<String>) {
            // 1. Vider l'ancienne grille
            destinationsGrid.removeAllViews()

            // 2. Générer les nouveaux boutons
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

        // announce = false pour le chargement par défaut à l'ouverture de la page,
        // pour ne pas répéter "Loading map" à chaque retour sur la page principale.
        fun loadMapAndGenerateButtons(mapName: String, announce: Boolean = true) {
            resetLocalInactivityTimer()

            // Points connus en dur pour cette carte
            val points = mapsData[mapName] ?: emptyList()
            currentActivePoints = points // Met à jour l'intelligence vocale !
            generateDestinationButtons(points)

            // Ordonner au robot de charger la carte
            RobotController.setMapName(mapName)
            RobotController.loadMap()
            if (announce) {
                RobotController.speak("Loading map for ${mapDisplayNames[mapName] ?: mapName}.")
            }
        }

        // On reprend la main sur le callback de carte prête (sinon celui de
        // FirstPage, désormais détaché, se déclencherait pendant nos chargements).
        // Les points sont connus en dur, il n'y a donc rien à faire ici.
        RobotController.setMapReadyCallback(object : RobotController.MapReadyCallback {
            override fun onMapIsReady() { /* points chargés en dur */ }
        })

        // Multi-robots (NYP RIG / NYP BOA) : chaque robot a sa propre carte du bloc
        // R4 (home base différente). On détecte le robot via son nom Bluetooth
        // ("NYP RIG" / "NYP BOA") pour afficher et charger la bonne carte R4.
        val robotName = android.provider.Settings.Secure.getString(
            requireContext().contentResolver, "bluetooth_name"
        ) ?: ""
        val r4MapName = if (robotName.contains("boa", ignoreCase = true))
            "R4 Block Complete (USE THIS) for BOA1"
        else
            "R4 Block Complete (USE THIS) for RIG1"

        // Boutons de cartes (à gauche de l'écran) : liste fixe — la carte R4 du
        // robot courant, puis les cartes communes aux deux robots.
        val mapsToShow = listOf(r4MapName, "S118", "Library level 4 (pls dont delete)1")
        val mapsListContainer = view.findViewById<android.widget.LinearLayout>(R.id.mapsListContainer)
        val mapBtnDensity = resources.displayMetrics.density
        for (name in mapsToShow) {
            val btn = Button(requireContext())
            btn.text = mapDisplayNames[name] ?: name
            btn.setTextColor(android.graphics.Color.WHITE)
            btn.textSize = 22f
            btn.setTypeface(null, android.graphics.Typeface.BOLD)
            btn.isAllCaps = false
            btn.setBackgroundResource(R.drawable.button_blue_transparent)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (100 * mapBtnDensity).toInt()
            )
            params.bottomMargin = (15 * mapBtnDensity).toInt()
            btn.layoutParams = params
            btn.setOnClickListener { loadMapAndGenerateButtons(name) }
            mapsListContainer.addView(btn)
        }

        // Par défaut, on charge la carte R4 du robot courant (sans annonce vocale).
        loadMapAndGenerateButtons(r4MapName, announce = false)
        // --- Camera frames for face recognition at wake ---
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            initCameraFrameProvider()
        } else {
            Log.w("MainPage", "Camera permission not granted — face recognition unavailable this session")
        }

        //-- LOGIQUE MULTILINGUE
        val langButton = view.findViewById<Button>(R.id.langButton)
        val langOverlay = view.findViewById<View>(R.id.langOverlay)
        val closeLangButton = view.findViewById<Button>(R.id.closeLangButton)




        fun changeLanguage(flagText: String, langCode: String, ttsLang: com.robotemi.sdk.TtsRequest.Language, locale: java.util.Locale) {
            resetLocalInactivityTimer()
            langOverlay.visibility = View.GONE
            langButton.text = flagText

            RobotController.updateLanguage(langCode, ttsLang, locale)

            val dict = uiTexts[langCode]
            if (dict != null) {
                view.findViewById<android.widget.TextView>(R.id.greetingText).text = dict["greeting"]
                view.findViewById<Button>(R.id.cardPool).text = dict["pool"]
                view.findViewById<Button>(R.id.cardLibrary).text = dict["lib"]
                view.findViewById<Button>(R.id.cardClubs).text = dict["club"]
                view.findViewById<Button>(R.id.cardBursary).text = dict["bursary"]
                view.findViewById<android.widget.EditText>(R.id.textInput).hint = dict["hint"]
                view.findViewById<Button>(R.id.interactionButton).text = dict["interaction"]
                view.findViewById<Button>(R.id.cardPhoto).text = dict["selfie"]
                view.findViewById<Button>(R.id.btnShowMap).text = dict["mapBtn"]
                view.findViewById<Button>(R.id.sendTextButton).text = dict["send"]
            }
            RobotController.speak("Language updated")
        }

        langButton.setOnClickListener { langOverlay.visibility = View.VISIBLE }
        closeLangButton.setOnClickListener { langOverlay.visibility = View.GONE }

        view.findViewById<Button>(R.id.btnLangEn).setOnClickListener { changeLanguage("🇬🇧 EN", "EN", com.robotemi.sdk.TtsRequest.Language.EN_US, java.util.Locale.US) }
        view.findViewById<Button>(R.id.btnLangFr).setOnClickListener { changeLanguage("🇫🇷 FR", "FR", com.robotemi.sdk.TtsRequest.Language.FR_FR, java.util.Locale.FRANCE) }
        view.findViewById<Button>(R.id.btnLangZh).setOnClickListener { changeLanguage("🇨🇳 ZH", "ZH", com.robotemi.sdk.TtsRequest.Language.ZH_CN, java.util.Locale.CHINA) }
        view.findViewById<Button>(R.id.btnLangMs).setOnClickListener { changeLanguage("🇲🇾 MS", "MS", com.robotemi.sdk.TtsRequest.Language.EN_US, java.util.Locale("ms", "MY")) }
        view.findViewById<Button>(R.id.btnLangJa).setOnClickListener { changeLanguage("🇯🇵 JA", "JA", com.robotemi.sdk.TtsRequest.Language.JA_JP, java.util.Locale.JAPAN) }
        view.findViewById<Button>(R.id.btnLangDe).setOnClickListener { changeLanguage("🇩🇪 DE", "DE", com.robotemi.sdk.TtsRequest.Language.DE_DE, java.util.Locale.GERMANY) }



        langButton.setOnClickListener { langOverlay.visibility = View.VISIBLE }
        closeLangButton.setOnClickListener { langOverlay.visibility = View.GONE }

        // 🟢 MISE À JOUR : On passe aussi la "Locale" pour forcer l'accent !
        view.findViewById<Button>(R.id.btnLangEn).setOnClickListener { changeLanguage("🇬🇧 EN", "EN", com.robotemi.sdk.TtsRequest.Language.EN_US, java.util.Locale.US) }
        view.findViewById<Button>(R.id.btnLangFr).setOnClickListener { changeLanguage("🇫🇷 FR", "FR", com.robotemi.sdk.TtsRequest.Language.FR_FR, java.util.Locale.FRANCE) }
        view.findViewById<Button>(R.id.btnLangZh).setOnClickListener { changeLanguage("🇨🇳 ZH", "ZH", com.robotemi.sdk.TtsRequest.Language.ZH_CN, java.util.Locale.CHINA) }
        view.findViewById<Button>(R.id.btnLangMs).setOnClickListener { changeLanguage("🇲🇾 MS", "MS", com.robotemi.sdk.TtsRequest.Language.EN_US, java.util.Locale("ms", "MY")) }
        view.findViewById<Button>(R.id.btnLangJa).setOnClickListener { changeLanguage("🇯🇵 JA", "JA", com.robotemi.sdk.TtsRequest.Language.JA_JP, java.util.Locale.JAPAN) }
        view.findViewById<Button>(R.id.btnLangDe).setOnClickListener { changeLanguage("🇩🇪 DE", "DE", com.robotemi.sdk.TtsRequest.Language.DE_DE, java.util.Locale.GERMANY) }

        // 🟢 Restaurer l'affichage du bouton de langue si on revient d'une autre page
        val currentDict = uiTexts[RobotController.currentLangCode]
        if (currentDict != null) {
            view.findViewById<Button>(R.id.cardPool).text = currentDict["pool"]
            view.findViewById<Button>(R.id.cardLibrary).text = currentDict["lib"]
            view.findViewById<Button>(R.id.cardClubs).text = currentDict["club"]
            view.findViewById<Button>(R.id.cardBursary).text = currentDict["bursary"]
            view.findViewById<android.widget.EditText>(R.id.textInput).hint = currentDict["hint"]
            view.findViewById<Button>(R.id.interactionButton).text = currentDict["interaction"]

            // Met à jour le drapeau en haut
            when(RobotController.currentLangCode) {
                "FR" -> langButton.text = "🇫🇷 FR"
                "ZH" -> langButton.text = "🇨🇳 ZH"
                "MS" -> langButton.text = "🇲🇾 MS"
                "JA" -> langButton.text = "🇯🇵 JA"
                "DE" -> langButton.text = "🇩🇪 DE"
                else -> langButton.text = "🇬🇧 EN"
            }
        }


        // --- Clics sur les 4 grandes cartes ---
        view.findViewById<Button>(R.id.cardPool).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady((it as Button).text.toString()) // Envoie la question traduite
        }
        view.findViewById<Button>(R.id.cardLibrary).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady((it as Button).text.toString())
        }
        view.findViewById<Button>(R.id.cardClubs).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady((it as Button).text.toString())
        }
        view.findViewById<Button>(R.id.cardBursary).setOnClickListener {
            resetLocalInactivityTimer()
            onRequestIsReady((it as Button).text.toString())
        }


        // Bouton pour lancer le mode Photo
        view.findViewById<Button>(R.id.cardPhoto)?.setOnClickListener {
            resetLocalInactivityTimer()
            RobotController.speak("Let's take a picture together!")
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PhotoPage())
                .addToBackStack(null)
                .commit()
        }



// bouton qu'on a dessiné dans le XML
        val btnShowMap = view.findViewById<Button>(R.id.btnShowMap)

//quoi faire au clic
        btnShowMap.setOnClickListener {
            RobotController.speak("Sure! Opening the campus map.")
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapPage()) // Ouvre la nouvelle page de la carte
                .commit()
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
        // On ajoute la langue entre crochets pour que ton script Python puisse la lire !
        (activity as MainActivity).userRequest = "[${RobotController.currentLangCode}] $request"

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

    private fun initCameraFrameProvider() {
        RobotController.setDetectionModeOn(false, 0.5f)
        cameraFrameProvider = CameraFrameProvider(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
        )
        cameraFrameProvider?.start()
    }

    // =========================================================================
    // WAVE DETECTOR — DÉSACTIVÉ le 2026-07-23 (trop de faux déclenchements).
    // Câblage d'origine conservé en commentaire comme preuve du travail réalisé.
    // Le geste de la main réveillait le robot / lançait l'écoute vocale.
    // Implémentation complète : voir detection/WaveGestureRecognizer.kt (commenté).
    // Pour réactiver : décommenter WaveGestureRecognizer.kt + ce bloc, et appeler
    // initWaveDetector() à la place de initCameraFrameProvider().
    //
    // private var waveRecognizer: WaveGestureRecognizer? = null
    //
    // private fun initWaveDetector() {
    //     RobotController.setDetectionModeOn(false, 0.5f)
    //     waveRecognizer = WaveGestureRecognizer(
    //         context = requireContext(),
    //         lifecycleOwner = viewLifecycleOwner,
    //     ) {
    //         requireActivity().runOnUiThread {
    //             TelemetryClient.track("wave")
    //             if (isSleeping) {
    //                 wakeUpSequence()
    //             } else {
    //                 // FIX : Le signe de la main active l'écoute vocale comme le bouton !
    //                 RobotController.setDetectionModeOn(false, 0.5f)
    //                 RobotController.setLastRequestTimeNow()
    //                 RobotController.stopMovement()
    //                 RobotController.resetInactivityTimer()
    //
    //                 // Récupère la phrase traduite pour parler
    //                 val currentDict = uiTexts[RobotController.currentLangCode]
    //                 val spokenGreeting = currentDict?.get("greeting") ?: "How can I help you?"
    //
    //                 RobotController.askQuestion(spokenGreeting)
    //                 TelemetryClient.track("greeting")
    //             }
    //         }
    //     }
    //     waveRecognizer?.start()
    // }
    // =========================================================================

    override fun onDestroyView() {
        super.onDestroyView()
        cameraFrameProvider?.stop()
        cameraFrameProvider = null
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

    private fun hideKeyboard(focusedView: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focusedView.windowToken, 0)
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
        // On endort le robot tête haute : elle reste ainsi bien orientée vers le
        // visage pendant toute la veille, et le réveil part déjà de la bonne position.
        RobotController.tiltHead(+55)
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
        // On relève la tête TOUT DE SUITE : au réveil elle est souvent basse (elle
        // vise le pantalon), ce qui casse le scan du visage. En l'appliquant dès le
        // début, elle a le temps de monter pendant l'animation de réveil (~6 s)
        // avant la capture de la frame pour la reconnaissance faciale.
        RobotController.tiltHead(+55)
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
                // Sécurité : on réaffirme la tête haute juste avant la capture, car
                // Temi remet parfois la tête à plat pendant l'animation de réveil.
                RobotController.tiltHead(+55)
                // On capture la frame ICI, à la fin de l'animation de réveil : la
                // personne s'est redressée pour regarder le robot (elle ne se penche
                // plus vers l'écran comme au moment du clic), donc son visage est
                // dans le champ de la caméra qui pointe vers le haut.
                recognizeAndGreet(cameraFrameProvider?.latestJpeg())
            }, 1000)
        }, 5000)
    }

    /**
     * Reconnaissance faciale au réveil : prend la dernière frame du CameraFrameProvider
     * et interroge le service face du Pi. Si la personne est connue, on la salue
     * par son prénom ; sinon message d'accueil générique. Aucune 2e caméra ouverte.
     */
    private val genericGreeting = "Hi, I'm Temi bot, please ask me a question. You can ask me by clicking the button or writing it with the keyboard on the screen."

    private fun recognizeAndGreet(jpeg: ByteArray?) {
        // Empêche la veille de se déclencher pendant la reconnaissance / l'enrôlement :
        // sinon le timer de 15 s endort le robot en plein milieu du flux.
        inactivityHandler.removeCallbacksAndMessages(null)

        if (jpeg == null) {
            RobotController.speak(genericGreeting)
            resetLocalInactivityTimer()
            return
        }
        FaceClient.recognize(jpeg) { result ->
            if (!isAdded) return@recognize
            Log.i("MainPage", "recognize -> match=${result?.match} name=${result?.name} score=${result?.score} reason=${result?.reason}")
            when {
                result == null -> {                        // échec réseau
                    RobotController.speak(genericGreeting)
                    resetLocalInactivityTimer()
                }
                result.match && result.name != null -> {   // reconnu
                    RobotController.speak("Hi ${result.name}! How can I help you today?")
                    TelemetryClient.track("greeting")
                    resetLocalInactivityTimer()
                }
                result.reason == "no_face" -> {             // aucun visage capté
                    RobotController.speak(genericGreeting)
                    resetLocalInactivityTimer()
                }
                else -> promptEnrollment()  // visage vu mais inconnu → enrôlement (la veille reste annulée)
            }
        }
    }

    /** Personne inconnue : on ouvre l'écran d'enrôlement dédié (aperçu live + multi-angles). */
    private fun promptEnrollment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EnrollPage())
            .addToBackStack(null)
            .commit()
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