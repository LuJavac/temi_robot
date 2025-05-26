package com.temi.temi_robot

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.chaquo.python.PyObject

import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.constants.HardButton
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotLiftedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.navigation.listener.OnDistanceToDestinationChangedListener
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission

class RobotController(private var defaultLocations: List<String>, private var module: PyObject, private val mediaPlayer: MediaPlayer):
    Robot.AsrListener,
    Robot.TtsListener,
    OnRobotReadyListener,
    OnDetectionStateChangedListener,
    OnGoToLocationStatusChangedListener,
    OnDistanceToDestinationChangedListener,
    OnRobotLiftedListener,
    OnRequestPermissionResultListener
{
    private val robot = Robot.getInstance() // Create robot object

    // Add listeners to robot instance
    init {
        robot.addAsrListener(this)
        robot.addTtsListener(this)
        robot.addOnRobotReadyListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnDistanceToDestinationChangedListener(this)
        robot.addOnRobotLiftedListener(this)
        robot.addOnRequestPermissionResultListener(this)
    }

    // Lists for Q&A
    private val answer_1 = "The Library is open from 8:30 AM to 8:00 PM, Mondays to Fridays, and from 8:30 AM to 12:00 noon on Saturdays. During the Mid-Semester Break, hours are reduced to 8:30 AM to 6:00 PM, Mondays to Fridays. The Library is closed on Sundays and Public Holidays."
    private val keywords1_1 = listOf("opening hours", "hours", "schedule", "timetable", "library hours", "operating hours")

    private val answer_2 = "You can check your loan account by logging in to the Library Portal using your NYP email address."
    private val keywords1_2 = listOf("loan records", "loan history", "borrowing history", "checked out items", "loan account")
    private val keywords2_2 = listOf("check", "view", "see", "access")

    private val answer_3 = "You can renew your borrowed items at the Self-Check Machines, the Information Services Counter, or online via the Library Portal."
    private val keywords1_3 = listOf("renew", "extend", "prolong")
    private val keywords2_3 = listOf("loans", "loan", "borrowed items", "checked out items", "borrowed item", "checked out item")

    private val answer_4 = "You cannot borrow any new Library materials until all overdue items are returned and any outstanding fines are cleared."
    private val keywords1_4 = listOf("borrow", "check out", "loan")
    private val keywords2_4 = listOf("overdue", "not returned", "late", "unreturned")

    private val answer_5 = "All fines and charges for lost materials must be paid using cashless methods. Accepted options include NETS, PayNow, and AXS."
    private val keywords1_5 = listOf("pay", "settle", "clear")
    private val keywords2_5 = listOf("fine", "fines", "penalties", "fees", "lost materials", "lost items")

    private val answer_6 = "NYP students and full-time staff can access the Library’s digital resources remotely using their NYP credentials."
    private val keywords1_6 = listOf("access", "use", "view", "find")
    private val keywords2_6 = listOf("e-resources", "digital resources", "online resources", "databases")

    private val answer_7 = "Lifestyle magazines are located at Level 4 Lifestyle Hub, while academic journals are available at Level 5 Centre Wing."
    private val keywords1_7 = listOf("magazines", "journals", "periodicals", "journal")
    private val keywords2_7 = listOf("location", "found", "where", "placed", "situated")

    private val answer_8 = "The Library of Things is a special collection of gadgets and educational kits designed to enhance learning. It includes items such as board games, robotic kits, and other interactive tools."
    private val keywords1_8 = listOf("Library of Things", "collection of gadgets", "educational kits", "learning tools")

    private val answer_9 = "You can book a library room for 2 hours if there are at least 3 students. Booking is available 5 days in advance via the Library or Student Portal."
    private val keywords1_9 = listOf("book", "reserve", "schedule")
    private val keywords2_9 = listOf("library room", "study room", "group room", "discussion room", "study rooms", "library rooms", "group rooms")

    private val answer_10 = "To get help from a librarian, please call the library hotline at 6550 0150. (Note: Assistance through a virtual assistant like Temi may be available in the future.)"
    private val keywords1_10 = listOf("contact", "talk to", "reach", "speak with")
    private val keywords2_10 = listOf("librarian", "library staff", "helpdesk", "information desk")

    private val answer_11 = "The loan period for books is 14 days. Items can be renewed twice if no one else has reserved them."
    private val keywords1_11 = listOf("loan period", "borrow duration", "due date")
    private val keywords2_11 = listOf("books", "items", "materials", "book", "item")

    private val answer_12 = "If an item is reserved by another user, you will not be able to renew it."
    private val keywords1_12 = listOf("renew", "extend", "prolong")
    private val keywords2_12 = listOf("reserved", "on hold", "requested")

    private val answer_13 = "Library lockers are available for temporary storage. They are located near the library entrance."
    private val keywords1_13 = listOf("locker", "storage", "store", "lockers", "storages", "stores")
    private val keywords2_13 = listOf("location", "where", "placed")

    private val answer_14 = "You can suggest a title for the library to acquire by filling in the recommendation form on the Library Portal."
    private val keywords1_14 = listOf("suggest", "recommend", "propose")
    private val keywords2_14 = listOf("title", "book", "resource", "material", "titles", "books", "resources", "materials")

    private val answer_15 = "Lost and found items are kept at the Information Services Counter."
    private val keywords1_15 = listOf("lost", "lost item", "missing item", "lost items", "missing items")
    private val keywords2_15 = listOf("location", "where", "found", "what")

    private val answer_16 = "Group study rooms come with whiteboards and power sockets."
    private val keywords1_16 = listOf("facilities", "features", "equipment")
    private val keywords2_16 = listOf("group study rooms", "discussion rooms")

    private val answer_17 = "Food and drinks are not allowed in the library except for water in spill-proof bottles."
    private val keywords1_17 = listOf("food", "drink", "eating", "beverages", "drinks", "beverage")
    private val keywords2_17 = listOf("allowed", "permitted", "can I bring")

    private val answer_18 = "Printing and photocopying services are available at the library with a valid student or staff ID."
    private val keywords1_18 = listOf("printing", "photocopying", "copy", "print", "copies")
    private val keywords2_18 = listOf("available", "where", "how to use")

    private val answer_19 = "There are quiet zones in the library marked with signage."
    private val keywords1_19 = listOf("quiet zone", "silent area", "study area", "quiet zones", "silent areas", "study areas")
    private val keywords2_19 = listOf("location", "where", "found")

    private val answer_20 = "Library fines can be avoided by returning items on time and renewing them before the due date."
    private val keywords1_20 = listOf("avoid", "prevent", "stop")
    private val keywords2_20 = listOf("fines", "late fees", "penalties","fine", "penalty", "charge")

    private val answer_21 = "You can return borrowed items using the book drop near the library entrance or at the service counter."
    private val keywords1_21 = listOf("return", "drop off", "give back")
    private val keywords2_21 = listOf("books", "items", "borrowed materials", "book", "item", "borrowed item")

    private val answer_22 = "Late returns will incur a fine. Please check the library portal for detailed fine policies."
    private val keywords1_22 = listOf("late", "overdue", "not on time")
    private val keywords2_22 = listOf("fine", "penalty", "charge")

    private val answer_23 = "Overdue book fines start at $0.50 per day, while media items may incur higher rates."
    private val keywords1_23 = listOf("fines", "penalties", "fees", "fine", "penalty", "charge", "fee")
    private val keywords2_23 = listOf("books", "media", "overdue items", "overdue item")

    private val answer_24 = "Yes, you can reserve books currently on loan via the library catalogue."
    private val keywords1_24 = listOf("reserve", "place hold", "book request")
    private val keywords2_24 = listOf("book", "item", "resource", "books", "items", "resources")

    private val answer_25 = "Log into your library account to view your borrowing history."
    private val keywords1_25 = listOf("borrowing history", "loan history", "checkout records")
    private val keywords2_25 = listOf("check", "view", "see")

    private val answer_26 = "No, items borrowed from NYP Library must be returned to the same library."
    private val keywords1_26 = listOf("return", "drop off")
    private val keywords2_26 = listOf("other libraries", "different location", "outside NYP", "other library")

    private val answer_27 = "Report any lost or damaged item immediately. You may need to pay a replacement and administrative fee."
    private val keywords1_27 = listOf("lost", "damaged", "broken")
    private val keywords2_27 = listOf("report", "inform", "notify")

    private val answer_28 = "You can access e-books and e-journals through the Library Portal by searching the online catalogue or databases."
    private val keywords1_28 = listOf("access", "view", "read")
    private val keywords2_28 = listOf("ebooks", "ejournals", "digital materials", "ebooks", "ejournal", "digital material")

    private val answer_29 = "Use the OneSearch tool on the library homepage to find books, articles, and media resources."
    private val keywords1_29 = listOf("search", "find", "look for")
    private val keywords2_29 = listOf("resources", "books", "articles", "media", "resource", "book", "article")

    private val answer_30 = "Yes, you can download e-books depending on the platform such as ProQuest or EBSCO."
    private val keywords1_30 = listOf("download", "save", "access offline")
    private val keywords2_30 = listOf("ebooks", "electronic books", "ebook", "electronic book")

    private val answer_31 = "Yes, databases like IEEE and JSTOR are accessible via the library’s subscribed services."
    private val keywords1_31 = listOf("databases", "IEEE", "JSTOR", "database", "I triple e")
    private val keywords2_31 = listOf("available", "access", "use")

    private val answer_32 = "Past-year exam papers are available in the Digital Repository or through your school’s LMS."
    private val keywords1_32 = listOf("exam papers", "past year papers", "old exams", "exam paper", "past year paper", "old exam")
    private val keywords2_32 = listOf("access", "find", "where")

    private val answer_33 = "Log into the Library Portal using your NYP network ID and password at lib.nyp.edu.sg."
    private val keywords1_33 = listOf("login", "sign in", "access")
    private val keywords2_33 = listOf("library portal", "account", "website")

    private val answer_34 = "There is no dedicated app, but the library website is mobile-friendly and fully functional."
    private val keywords1_34 = listOf("app", "application", "mobile")
    private val keywords2_34 = listOf("library", "access", "services")

    private val answer_35 = "Use the online booking system via the Library Portal to book a group discussion room."
    private val keywords1_35 = listOf("book", "reserve", "schedule")
    private val keywords2_35 = listOf("group discussion room", "study room", "study rooms", "group rooms", "group room", "discussion room", "discussion rooms")

    private val answer_36 = "Printing, copying, and scanning services are available with your student or staff card."
    private val keywords1_36 = listOf("printing", "copying", "scanning")
    private val keywords2_36 = listOf("available", "use", "where")

    private val answer_37 = "To print from your laptop, install the NYP network printer or use WebPrint via the NYP intranet."
    private val keywords1_37 = listOf("print", "send to printer")
    private val keywords2_37 = listOf("laptop", "computer", "device", "laptops", "computers", "devices")

    private val answer_38 = "Yes, desktop computers are available in the library on a first-come-first-served basis."
    private val keywords1_38 = listOf("computers", "PCs", "desktops", "computer", "PC", "desktop")
    private val keywords2_38 = listOf("available", "use", "access")

    private val answer_39 = "Yes, the library includes makerspaces and innovation labs depending on the current initiatives."
    private val keywords1_39 = listOf("makerspace", "makerspaces", "tech corner", "labs", "lab", "tech corners")
    private val keywords2_39 = listOf("available", "have", "exist")

    private val answer_40 = "Wi-Fi is available in the library. Connect using your NYP network credentials."
    private val keywords1_40 = listOf("wifi", "wireless", "internet")
    private val keywords2_40 = listOf("connect", "available", "access")

    private val answer_41 = "You can bring your own laptop to the library and use the available power sockets and Wi-Fi."
    private val keywords1_41 = listOf("laptop", "computer", "device", "laptops", "computers", "devices")
    private val keywords2_41 = listOf("bring", "use", "allowed")

    private val answer_42 = "You can suggest library events or workshops through the feedback form on the Library Portal."
    private val keywords1_42 = listOf("suggest", "propose", "recommend")
    private val keywords2_42 = listOf("events", "workshops", "programs", "event", "program")

    private val answer_43 = "Workshops and training sessions are announced on the Library Portal and via email notifications."
    private val keywords1_43 = listOf("workshop", "training", "session", "workshops", "training sessions", "sessions")
    private val keywords2_43 = listOf("schedule", "when", "announcement")

    private val answer_44 = "Orientation tours are available for new students at the start of each semester."
    private val keywords1_44 = listOf("orientation", "tour", "introduction", "tours")
    private val keywords2_44 = listOf("available", "offered", "schedule")

    private val answer_45 = "You may request help from a librarian for your research project by scheduling a consultation."
    private val keywords1_45 = listOf("help", "assistance", "guidance")
    private val keywords2_45 = listOf("research", "project", "assignment")

    private val answer_46 = "The library provides citation guides and workshops to help you with referencing."
    private val keywords1_46 = listOf("citation", "referencing", "sources", "source", "citations")
    private val keywords2_46 = listOf("guide", "help", "support")

    private val answer_47 = "Plagiarism detection tools like Turnitin are accessible through your school’s LMS."
    private  val keywords1_47 = listOf("plagiarism", "copying", "originality")
    private val keywords2_47 = listOf("tool", "detection", "turn it in", "tools")

    private val answer_48 = "Books borrowed by mistake can be returned at the service counter without penalty if done promptly."
    private val keywords1_48 = listOf("wrong book", "mistake", "accidental borrow")
    private val keywords2_48 = listOf("return", "give back", "undo")

    private val answer_49 = "Yes, alumni can access selected digital resources. Check the Alumni Portal for eligibility."
    private val keywords1_49 = listOf("alumni", "graduates", "former students", "alumni", "graduate", "former student")
    private val keywords2_49 = listOf("access", "resources", "library", "resource")

    private val answer_50 = "Library staff can help you locate hard-to-find materials or place an inter-library loan request."
    private val keywords1_50 = listOf("find", "locate", "track down")
    private val keywords2_50 = listOf("materials", "resources", "books", "material", "resource", "book")

    private val answer_51 = "Children are allowed in the library if accompanied by an NYP student or staff."
    private val keywords1_51 = listOf("children", "kids", "minors", "child", "kid", "minor")
    private val keywords2_51 = listOf("allowed", "can", "permitted")

    private val answer_52 = "Noise complaints can be reported to library staff at the service counter."
    private val keywords1_52 = listOf("noise", "loud", "disturbance", "noises")
    private val keywords2_52 = listOf("report", "complain", "notify")

    private val answer_53 = "Headphones can be borrowed from the Information Services Counter."
    private val keywords1_53 = listOf("headphones", "earphones", "audio device")
    private val keywords2_53 = listOf("borrow", "loan", "get")

    private val answer_54 = "Yes, the library offers book displays and themed exhibits regularly."
    private val keywords1_54 = listOf("exhibits", "displays", "book themes", "exhibit", "display", "book theme")
    private val keywords2_54 = listOf("available", "offered", "schedule")

    private val answer_55 = "Yes, your feedback is welcome. Use the feedback form on the Library Portal."
    private val keywords1_55 = listOf("feedback", "comment", "suggestion", "feedbacks", "comments", "suggestions")
    private val keywords2_55 = listOf("submit", "give", "send")

    private val answer_56 = "The library has height-adjustable tables and other accessible facilities for users with disabilities."
    private val keywords1_56 = listOf("accessible", "disability", "wheelchair")
    private val keywords2_56 = listOf("facilities", "equipment", "support", "facilities", "equipment")

    private val answer_57 = "Yes, library tours are available for visiting groups upon request."
    private val keywords1_57 = listOf("tours", "visit", "orientation", "tour", "visits")
    private val keywords2_57 = listOf("group", "request", "available", "requests", "groups")

    private val answer_58 = "Extended hours are offered during exam periods. Check the Library Portal for updates."
    private val keywords1_58 = listOf("extended hours", "longer hours", "exam time")
    private val keywords2_58 = listOf("schedule", "available", "open")

    private val answer_59 = "Library news and updates are shared via the portal, email, and digital screens on campus."
    private val keywords1_59 = listOf("news", "updates", "announcements", "update", "announcement")
    private val keywords2_59 = listOf("where", "find", "access")

    private val answer_60 = "You can volunteer at the library by applying through the student development office or library website."
    private val keywords1_60 = listOf("volunteer", "help", "assist", "volunteers")
    private val keywords2_60 = listOf("library", "apply", "how", "become")

    //Go To locations
    private val answer_61= "Please follow me, we are going to the think space."
    private val keywords1_61 = listOf("think space")
    private val questions = listOf("where", "go", "take me", "find")

    private val answer_62= "Please follow me, we are going to the dream space."
    private val keywords1_62 = listOf("dream space", "dreaming space")

    private val answer_63= "Please follow me, we are going to the idea space."
    private val keywords1_63 = listOf("idea space")

    private val answer_64= "Please follow me, we are going to the smart learning hub."
    private val keywords1_64 = listOf("smart learning hub", "smart learning")

    private val answer_65= "Please follow me, we are going to the management collection."
    private val keywords1_65 = listOf("management","management collection", "books")

    private val answer_66= "Please follow me, we are going to the learn for life pod."
    private val keywords1_66 = listOf("learn life pod")

    private val answer_67= "Please follow me, we are going to the dvds."
    private val keywords1_67 = listOf("dvds", "cds", "dvd", "cd")

    private val answer_68= "Please follow me, we are going to the smart kiosk."
    private val keywords1_68 = listOf("smart kiosk")

    private val answer_69= "Please follow me, we are going to the exhibition."
    private val keywords1_69 = listOf("exhibition")

    private val answer_70= "Please follow me, we are going to the book recommendations."
    private val keywords1_70 = listOf("book recommendations")

    private val answer_71= "Please follow me, we are going to the magazines."
    private val keywords1_71 = listOf("magazines", "magazines collection", "magazines books")

    private val answer_72= "Please follow me, we are going to the lifestyle books."
    private val keywords1_72 = listOf("lifestyle books")

    private val answer_73= "Please follow me, we are going to the cafe."
    private val keywords1_73 = listOf("cafe")

    private val answer_74= "Please follow me, we are going to the smart space."
    private val keywords1_74 = listOf("smart space")

    private val answer_75= "Please follow me, we are going to the design collection."
    private val keywords1_75 = listOf("design collection")

    private val answer_76= "Please follow me, we are going to the health sciences."
    private val keywords1_76 = listOf("health sciences")

    private val answer_77= "Please follow me, we are going to the life sciences collection."
    private val keywords1_77 = listOf("life sciences")


    // Time values
    private var lastRequestTime = 0L //
    private val requestCooldownMillis = 20000L // 50 seconds

    // Inactivity handling
    private var inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if(!isPermissionRequest){
            patrol(defaultLocations)
            robot.setDetectionModeOn(true, 0.5f)
        }
    }

    fun resetInactivityTimer() {
        println("reset")
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, 20_000) // 20 seconds
    }

    // Own variables
    private var isMoveRequest = false
    private var isPermissionRequest = false

    private var readyCallback: RobotReadyCallback? = null

    /////////// General functions

    // Getters and setters
    fun getDefaultLocations() : List<String> {
        return defaultLocations
    }

    fun setDefaultLocations(locations: List<String>) {
        defaultLocations = locations
    }

    // Own functions
    private fun changeLocationsOrder() {
        defaultLocations = defaultLocations.drop(1) + defaultLocations.first()
    }

    fun setLastRequestTimeNow(){
        lastRequestTime = System.currentTimeMillis()
    }

    private fun isIntoList(request: String, list1: List<String>, list2: List<String> = emptyList()): Boolean {
        if(list2.isEmpty()){
            return list1.any { word -> request.contains(word, ignoreCase = true) }
        } else {
            return list1.any { word -> request.contains(word, ignoreCase = true) } && list2.any { word -> request.contains(word, ignoreCase = true) }
        }
    }

    // System functions
    fun hideTopBar()
    {
        robot.hideTopBar()
    }

    fun setVolume(volume : Int){
        robot.volume = volume
    }

    fun toggleWakeup(disabled : Boolean){
        robot.toggleWakeup(disabled)
    }

    fun setTopBadgeEnabled(enabled : Boolean){
        robot.topBadgeEnabled = enabled
    }

    fun setHardButtonMode(type : HardButton, mode : HardButton.Mode){
        robot.setHardButtonMode(type, mode)
    }

    // Speech
    fun speak(speech: String, haveFace: Boolean = true) { // Make the robot speak
        // Creating TTS request before speaking
        val request = TtsRequest.create(
            speech = speech,
            isShowOnConversationLayer = false,
            showAnimationOnly = haveFace,
            language = TtsRequest.Language.EN_US
        )
        // Speaking with sdk function
        robot.speak(request)
    }

    fun askQuestion(question: String) {
        robot.askQuestion(question)
    }


    // Movements and map
    fun getLocations() : List<String> {
        return robot.locations
    }

    fun goTo(location: String) {
        isMoveRequest = true
        robot.goTo(location)
    }

    fun patrol(locations : List<String>){
        robot.patrol(locations, times = 0)
    }

    fun stopMovement(){
        robot.stopMovement()
    }

    // Person Detection
    fun setDetectionModeOn(on : Boolean, distance : Float){
        robot.setDetectionModeOn(on, distance)
    }

    fun isDetectionModeOn() : Boolean {
        return robot.detectionModeOn
    }


    // Permissions
    fun checkSelfPermission(permission: Permission) : Int{
        return robot.checkSelfPermission(permission)
    }

    fun requestPermissions(permissions: List<Permission>, requestCode: Int = 4){
        robot.requestPermissions(permissions, requestCode)
    }

    fun askRequiredPermissions(): Boolean {
        when {
            checkSelfPermission(Permission.SETTINGS) == 0 -> {
                isPermissionRequest = true
                speak("Please allow the settings permission for the application to work properly")
                requestPermissions(listOf(Permission.SETTINGS))
                return false
            }
            checkSelfPermission(Permission.MAP) == 0 -> {
                isPermissionRequest = true
                speak("Please allow the map permission for the application to work properly")
                requestPermissions(listOf(Permission.MAP))
                return false
            }
        }
        return true
    }

    // Own interface and callbacks
    interface RobotReadyCallback {
        fun onRobotIsReady()
    }

    fun setRobotReadyCallback(callback: RobotReadyCallback) {
        this.readyCallback = callback
    }


    // Overrides
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        resetInactivityTimer()
        if (isMoveRequest || isPermissionRequest) {
            return
        }

        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            patrol(defaultLocations)
            robot.setDetectionModeOn(true, 0.5f)
        }
    }

    override fun onDetectionStateChanged(state: Int) {
        if(isPermissionRequest || isMoveRequest){
            return
        }
        resetInactivityTimer()
        if (state == 2) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRequestTime < requestCooldownMillis) {
                return
            }
            lastRequestTime = currentTime

            robot.stopMovement()
            robot.setDetectionModeOn(false, 0.5f)
            robot.askQuestion("Hi, how can I help you ?")
        }
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        if(status == OnGoToLocationStatusChangedListener.COMPLETE){
            if(isMoveRequest){
                isMoveRequest = false
                speak("We arrived")
            } else {
                changeLocationsOrder()
            }
        }
    }

    override fun onDistanceToDestinationChanged(location: String, distance: Float) {
        resetInactivityTimer()
    }

    override fun onRobotReady(isReady: Boolean) {
        if(isReady){
            readyCallback?.onRobotIsReady()
        }
    }

    override fun onRequestPermissionResult(
        permission: Permission,
        grantResult: Int,
        requestCode: Int
    ) {
        if(grantResult == 0){
            speak("You need to grant the permission to make the application work properly. Please restart the application and do it again")
        } else {
            speak("please restart the application after granting a new permission")
        }
    }

    override fun onRobotLifted(isLifted: Boolean, reason: String) {
        println("lifted")
        if(isLifted){
            if (mediaPlayer.isPlaying) {
                mediaPlayer.seekTo(0)
            } else {
                mediaPlayer.start()
            }
        }
    }

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        resetInactivityTimer()
        if(isIntoList(asrResult, keywords1_1)){
            robot.finishConversation()
            speak(answer_1)
        } else if(isIntoList(asrResult, keywords1_2, keywords2_2)){
            robot.finishConversation()
            speak(answer_2)
        } else if(isIntoList(asrResult, keywords1_3, keywords2_3)){
            robot.finishConversation()
            speak(answer_3)
        } else if(isIntoList(asrResult, keywords1_4, keywords2_4)){
            robot.finishConversation()
            speak(answer_4)
        } else if(isIntoList(asrResult, keywords1_5, keywords2_5)){
            robot.finishConversation()
            speak(answer_5)
        } else if(isIntoList(asrResult, keywords1_6, keywords2_6)){
            robot.finishConversation()
            speak(answer_6)
        } else if(isIntoList(asrResult, keywords1_7, keywords2_7)){
            robot.finishConversation()
            speak(answer_7)
        } else if(isIntoList(asrResult, keywords1_8)){
            robot.finishConversation()
            speak(answer_8)
        } else if (isIntoList(asrResult, keywords1_9, keywords2_9)){
            robot.finishConversation()
            speak(answer_9)
        } else if (isIntoList(asrResult, keywords1_10, keywords2_10)){
            robot.finishConversation()
            speak(answer_10)
        } else if (isIntoList(asrResult, keywords1_11, keywords2_11)){
            robot.finishConversation()
            speak(answer_11)
        } else if (isIntoList(asrResult, keywords1_12, keywords2_12)){
            robot.finishConversation()
            speak(answer_12)
        } else if (isIntoList(asrResult, keywords1_13, keywords2_13)){
            robot.finishConversation()
            speak(answer_13)
        } else if (isIntoList(asrResult, keywords1_14, keywords2_14)){
            robot.finishConversation()
            speak(answer_14)
        } else if (isIntoList(asrResult, keywords1_15, keywords2_15)){
            robot.finishConversation()
            speak(answer_15)
        } else if (isIntoList(asrResult, keywords1_16, keywords2_16)){
            robot.finishConversation()
            speak(answer_16)
        }
        else if (isIntoList(asrResult, keywords1_17, keywords2_17)){
            robot.finishConversation()
            speak(answer_17)
        }
        else if (isIntoList(asrResult, keywords1_18, keywords2_18)){
            robot.finishConversation()
            speak(answer_18)
        }
        else if (isIntoList(asrResult, keywords1_19, keywords2_19)){
            robot.finishConversation()
            speak(answer_19)
        }
        else if (isIntoList(asrResult, keywords1_20, keywords2_20)){
            robot.finishConversation()
            speak(answer_20)
        }
        else if (isIntoList(asrResult, keywords1_21, keywords2_21)){
            robot.finishConversation()
            speak(answer_21)
        }
        else if (isIntoList(asrResult, keywords1_22, keywords2_22)){
            robot.finishConversation()
            speak(answer_22)
        }
        else if (isIntoList(asrResult, keywords1_23, keywords2_23)){
            robot.finishConversation()
            speak(answer_23)
        }
        else if (isIntoList(asrResult, keywords1_24, keywords2_24)){
            robot.finishConversation()
            speak(answer_24)
        }
        else if (isIntoList(asrResult, keywords1_25, keywords2_25)){
            robot.finishConversation()
            speak(answer_25)
        }
        else if (isIntoList(asrResult, keywords1_26, keywords2_26)){
            robot.finishConversation()
            speak(answer_26)
        }
        else if (isIntoList(asrResult, keywords1_27, keywords2_27)){
            robot.finishConversation()
            speak(answer_27)
        }
        else if (isIntoList(asrResult, keywords1_28, keywords2_28)){
            robot.finishConversation()
            speak(answer_28)
        }
        else if (isIntoList(asrResult, keywords1_29, keywords2_29)){
            robot.finishConversation()
            speak(answer_29)
        }
        else if (isIntoList(asrResult, keywords1_30, keywords2_30)){
            robot.finishConversation()
            speak(answer_30)
        }
        else if (isIntoList(asrResult, keywords1_31, keywords2_31)){
            robot.finishConversation()
            speak(answer_31)
        }
        else if (isIntoList(asrResult, keywords1_32, keywords2_32)){
            robot.finishConversation()
            speak(answer_32)
        }
        else if (isIntoList(asrResult, keywords1_33, keywords2_33)){
            robot.finishConversation()
            speak(answer_33)
        }
        else if (isIntoList(asrResult, keywords1_34, keywords2_34)){
            robot.finishConversation()
            speak(answer_34)
        }
        else if (isIntoList(asrResult, keywords1_35, keywords2_35)){
            robot.finishConversation()
            speak(answer_35)
        }
        else if (isIntoList(asrResult, keywords1_36, keywords2_36)){
            robot.finishConversation()
            speak(answer_36)
        }
        else if (isIntoList(asrResult, keywords1_37, keywords2_37)){
            robot.finishConversation()
            speak(answer_37)
        }
        else if (isIntoList(asrResult, keywords1_38, keywords2_38)){
            robot.finishConversation()
            speak(answer_38)
        }
        else if (isIntoList(asrResult, keywords1_39, keywords2_39)){
            robot.finishConversation()
            speak(answer_39)
        }
        else if (isIntoList(asrResult, keywords1_40, keywords2_40)){
            robot.finishConversation()
            speak(answer_40)
        }
        else if (isIntoList(asrResult, keywords1_41, keywords2_41)){
            robot.finishConversation()
            speak(answer_41)
        }
        else if (isIntoList(asrResult, keywords1_42, keywords2_42)){
            robot.finishConversation()
            speak(answer_42)
        }
        else if (isIntoList(asrResult, keywords1_43, keywords2_43)){
            robot.finishConversation()
            speak(answer_43)
        }
        else if (isIntoList(asrResult, keywords1_44, keywords2_44)){
            robot.finishConversation()
            speak(answer_44)
        }
        else if (isIntoList(asrResult, keywords1_45, keywords2_45)){
            robot.finishConversation()
            speak(answer_45)
        }
        else if (isIntoList(asrResult, keywords1_46, keywords2_46)){
            robot.finishConversation()
            speak(answer_46)
        }
        else if (isIntoList(asrResult, keywords1_47, keywords2_47)){
            robot.finishConversation()
            speak(answer_47)
        }
        else if (isIntoList(asrResult, keywords1_48, keywords2_48)){
            robot.finishConversation()
            speak(answer_48)
        }
        else if (isIntoList(asrResult, keywords1_49, keywords2_49)){
            robot.finishConversation()
            speak(answer_49)
        }
        else if (isIntoList(asrResult, keywords1_50, keywords2_50)){
            robot.finishConversation()
            speak(answer_50)
        }
        else if (isIntoList(asrResult, keywords1_51, keywords2_51)){
            robot.finishConversation()
            speak(answer_51)
        }
        else if (isIntoList(asrResult, keywords1_52, keywords2_52)){
            robot.finishConversation()
            speak(answer_52)
        }
        else if (isIntoList(asrResult, keywords1_53, keywords2_53)){
            robot.finishConversation()
            speak(answer_53)
        }
        else if (isIntoList(asrResult, keywords1_54, keywords2_54)){
            robot.finishConversation()
            speak(answer_54)
        }
        else if (isIntoList(asrResult, keywords1_55, keywords2_55)){
            robot.finishConversation()
            speak(answer_55)
        }
        else if (isIntoList(asrResult, keywords1_56, keywords2_56)){
            robot.finishConversation()
            speak(answer_56)
        }
        else if (isIntoList(asrResult, keywords1_57, keywords2_57)){
            robot.finishConversation()
            speak(answer_57)
        }
        else if (isIntoList(asrResult, keywords1_58, keywords2_58)){
            robot.finishConversation()
            speak(answer_58)
        }
        else if (isIntoList(asrResult, keywords1_59, keywords2_59)){
            robot.finishConversation()
            speak(answer_59)
        }
        else if (isIntoList(asrResult, keywords1_60, keywords2_60)){
            robot.finishConversation()
            speak(answer_60)
        }
        //Go to locations
        else if (isIntoList(asrResult, keywords1_61, questions)){
            robot.finishConversation()
            speak(answer_61)
            goTo("think space")
        }
        else if (isIntoList(asrResult, keywords1_62, questions)){
            robot.finishConversation()
            speak(answer_62)
            goTo("dream space")
        }
        else if (isIntoList(asrResult, keywords1_63, questions)){
            robot.finishConversation()
            speak(answer_63)
            goTo("idea space")
        }
        else if (isIntoList(asrResult, keywords1_64, questions)){
            robot.finishConversation()
            speak(answer_64)
            goTo("smart learning hub")
        }
        else if (isIntoList(asrResult, keywords1_65, questions)){
            robot.finishConversation()
            speak(answer_65)
            goTo("management collection")
        }
        else if (isIntoList(asrResult, keywords1_66, questions)){
            robot.finishConversation()
            speak(answer_66)
            goTo("learn for life pod")
        }
        else if (isIntoList(asrResult, keywords1_67, questions)){
            robot.finishConversation()
            speak(answer_67)
            goTo("dvds")
        }
        else if (isIntoList(asrResult, keywords1_68, questions)){
            robot.finishConversation()
            speak(answer_68)
            goTo("smart kiosk")
        }
        else if (isIntoList(asrResult, keywords1_69, questions)){
            robot.finishConversation()
            speak(answer_69)
            goTo("exhibition")
        }
        else if (isIntoList(asrResult, keywords1_70, questions)){
            robot.finishConversation()
            speak(answer_70)
            goTo("book recommendations")
        }
        else if (isIntoList(asrResult, keywords1_71, questions)){
            robot.finishConversation()
            speak(answer_71)
            goTo("magazines")
        }
        else if (isIntoList(asrResult, keywords1_72, questions)){
            robot.finishConversation()
            speak(answer_72)
            goTo("lifestyle books")
        }
        else if (isIntoList(asrResult, keywords1_73, questions)){
            robot.finishConversation()
            speak(answer_73)
            goTo("cafe")
        }
        else if (isIntoList(asrResult, keywords1_74, questions)){
            robot.finishConversation()
            speak(answer_74)
            goTo("smart space")
        }
        else if (isIntoList(asrResult, keywords1_75, questions)){
            robot.finishConversation()
            speak(answer_75)
            goTo("design collection")
        }
        else if (isIntoList(asrResult, keywords1_76, questions)){
            robot.finishConversation()
            speak(answer_76)
            goTo("health sciences")
        }
        else if (isIntoList(asrResult, keywords1_77, questions)){
            robot.finishConversation()
            speak(answer_77)
            goTo("life sciences collection")
        }
        else {
            robot.finishConversation()
            val result = module.callAttr("get_response", asrResult)
            if (result != null) {
                val response = result.toString()
                speak(response)
            } else {
                speak("chatbot error")
            }
        }
    }
}