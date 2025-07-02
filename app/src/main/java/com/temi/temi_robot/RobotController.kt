package com.temi.temi_robot

import android.os.Handler
import android.os.Looper

import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.constants.HardButton
import com.robotemi.sdk.constants.Platform
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.listeners.OnTelepresenceStatusChangedListener
import com.robotemi.sdk.map.OnLoadMapStatusChangedListener
import com.robotemi.sdk.navigation.listener.OnDistanceToDestinationChangedListener
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.telepresence.CallState
import com.robotemi.sdk.telepresence.Participant
import com.temi.temi_robot.dataclasses.PatrolStates
import java.lang.ref.WeakReference

// Robot controller class
object RobotController:
    Robot.AsrListener,
    Robot.TtsListener,
    OnRobotReadyListener,
    OnDetectionStateChangedListener,
    OnGoToLocationStatusChangedListener,
    OnDistanceToDestinationChangedListener,
    OnRequestPermissionResultListener,
    OnLoadMapStatusChangedListener,
    OnTelepresenceStatusChangedListener(sessionId = "")
{
    private var robotRef: WeakReference<Robot>? = null
    private var mapName: String? = null
    private lateinit var patrolStates: PatrolStates

    // Add listeners to robot instance
    fun setListeners(){
        getRobot()?.addAsrListener(this)
        getRobot()?.addTtsListener(this)
        getRobot()?.addOnRobotReadyListener(this)
        getRobot()?.addOnDetectionStateChangedListener(this)
        getRobot()?.addOnGoToLocationStatusChangedListener(this)
        getRobot()?.addOnDistanceToDestinationChangedListener(this)
        getRobot()?.addOnRequestPermissionResultListener(this)
        getRobot()?.addOnLoadMapStatusChangedListener(this)
        getRobot()?.addOnTelepresenceStatusChangedListener(this)   
    }

    // Lists of keywords for approving or denying librarian call request
    private val approvedKeywords = listOf("yes", "please", "of course", "sure", "ok")
    private val deniedKeywords =  listOf("no", "don't", "not")

    //Go To locations
    private val answer_61= "Please follow me, we are going to the think space."
    private val keywords1_61 = listOf("think space")
    private val questions = listOf("where", "go", "take me", "find", "bring me")

    private val answer_62= "Please follow me, we are going to the dream space."
    private val keywords1_62 = listOf("dream space", "dreaming space")

    private val answer_63= "Please follow me, we are going to the idea space."
    private val keywords1_63 = listOf("idea space")

    private val answer_64= "Please follow me, we are going to the smart learning hub."
    private val keywords1_64 = listOf("smart learning hub", "smart learning", "learning hub")

    private val answer_65= "Please follow me, we are going to the management collection."
    private val keywords1_65 = listOf("management")

    private val answer_66= "Please follow me, we are going to the learn for life pod."
    private val keywords1_66 = listOf("learn life pod", "learn for life")

    private val answer_67= "Please follow me, we are going to the dvds."
    private val keywords1_67 = listOf("dvds", "cds", "dvd", "cd")

    private val answer_68= "Please follow me, we are going to the smart kiosk."
    private val keywords1_68 = listOf("kiosk")

    private val answer_69= "Please follow me, we are going to the exhibition."
    private val keywords1_69 = listOf("exhibition")

    private val answer_70= "Please follow me, we are going to the book recommendations."
    private val keywords1_70 = listOf("book recommendations")

    private val answer_71= "Please follow me, we are going to the magazines."
    private val keywords1_71 = listOf("magazines", "magazines collection", "magazines books")

    private val answer_72= "Please follow me, we are going to the lifestyle books."
    private val keywords1_72 = listOf("lifestyle books", "lifestyle book", "life style")

    private val answer_73= "Please follow me, we are going to the cafe."
    private val keywords1_73 = listOf("cafe", "cafeteria")

    private val answer_74= "Please follow me, we are going to the smart space."
    private val keywords1_74 = listOf("smart space")

    private val answer_75= "Please follow me, we are going to the design collection."
    private val keywords1_75 = listOf("design")

    private val answer_76= "Please follow me, we are going to the health sciences."
    private val keywords1_76 = listOf("health sciences", "health science")

    private val answer_77= "Please follow me, we are going to the life sciences collection."
    private val keywords1_77 = listOf("life sciences", "health science")

    private val answer_78= "Please follow me, we are going to the fiction books."
    private val keywords1_78 = listOf("fiction")

    private val answer_79= "Please follow me, we are going to the project reports."
    private val keywords1_79 = listOf("project reports", "project papers", "project report", "project paper")

    private val answer_80= "Please follow me, we are going to the photocopying stations."
    private val keywords1_80 = listOf("photocopy", "copy", "photocopying", "print", "printing")

    private val answer_81= "Please follow me, we are going to the performance stage."
    private val keywords1_81 = listOf("performance", "performances")

    private val answer_82= "Please follow me, we are going to the lifestyle media."
    private val keywords1_82 = listOf("lifestyle media")

    private val answer_83= "Please follow me, we are going to see this racist."
    private val keywords1_83 = listOf("jason")


    // Time values
    private var lastRequestTime = 0L //
    private val requestCooldownMillis = 20000L // 20 seconds

    // Own variables
    private var isMoveRequest = false
    private var blockMode = false
    private var isAskSatisfiedRequest = false
    private var isDoNotEatSpeech = false
    private var isAtHomeBase = true

    private var readyCallback: RobotReadyCallback? = null
    private var mapReadyCallback: MapReadyCallback? = null
    private var requestReadyCallback: RequestReadyCallback? = null
    private var backToMainPageCallback: BackToMainPageCallback? = null
    private var backToBaseCallback: BackToBaseCallback? = null
    private var meetingStartedCallback: MeetingStartedCallback? = null

    /////////// General functions

    // Getters and setters
    fun setRobot(robot: Robot) {
        robotRef = WeakReference(robot)
    }

    fun getRobot(): Robot? = robotRef?.get()

    fun setMapName(name: String) {
        mapName = name
    }

    fun setBlockMode(value: Boolean) {
        blockMode = value
        setDetectionModeOn(!value, 0.5f)
    }

    fun setPatrolStates(patrolStates: PatrolStates){
        this.patrolStates = patrolStates
    }

    fun getPatrolStates(): PatrolStates {
        return this.patrolStates
    }

    fun isAtHomeBase(): Boolean{
        return isAtHomeBase

    }

    fun setAtHomeBase(value: Boolean){
        isAtHomeBase = value
    }

    fun setLastRequestTimeNow(){
        lastRequestTime = System.currentTimeMillis()
    }

    fun setSatisfiedRequest(value: Boolean){
        isAskSatisfiedRequest = value
    }
    fun setMoveRequest(value: Boolean){
        isMoveRequest = value
    }

    // Custom functions
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
        getRobot()?.hideTopBar()
    }

    fun setVolume(volume : Int){
        getRobot()?.volume = volume
    }

    fun toggleWakeup(disabled : Boolean){
        getRobot()?.toggleWakeup(disabled)
    }

    fun setTopBadgeEnabled(enabled : Boolean){
        getRobot()?.topBadgeEnabled = enabled
    }

    fun setHardButtonMode(type : HardButton, mode : HardButton.Mode){
        getRobot()?.setHardButtonMode(type, mode)
    }

    // Speech
    fun speak(speech: String, subtitles: Boolean = true) { // Make the robot speak
        // Creating TTS request before speaking
        val request = TtsRequest.create(
            speech = speech,
            isShowOnConversationLayer = subtitles,
            showAnimationOnly = !subtitles,
            language = TtsRequest.Language.EN_US
        )
        // Speaking with sdk function
        getRobot()?.speak(request)
    }

    fun askQuestion(question: String) {
        getRobot()?.askQuestion(question)
    }
    
    fun finishConversation(){
        getRobot()?.finishConversation()
    }


    // Movements and map
    fun loadMap() {
        val maps = getRobot()?.getMapList()
        val map = maps?.find { it.name == mapName }
        if(map == null){
            patrolStates.setLocations(emptyList())
            readyCallback?.onRobotIsReady()
        }
        else {
            getRobot()?.loadMap(map.id)
        }
    }

    fun goTo(location: String) {
        setDetectionModeOn(false, 0.5f)
        println(getRobot()?.detectionModeOn)
        isMoveRequest = true
        getRobot()?.goTo(location)
    }

    fun patrol(){
        // Trigger patrolling only if robot is not at home base
        if(!isAtHomeBase){
            setBlockMode(false)
            getRobot()?.patrol(patrolStates.getPatrolLocations(), times = 0)
            startPeriodicSpeech(15)
        }
    }

    fun stopMovement(){
        getRobot()?.stopMovement()
    }

    fun goToHomeBase(){
        goTo("home base")
        println(getRobot()?.detectionModeOn)
    }

    fun tiltHead(angle : Int){
        getRobot()?.tiltAngle(angle)
    }

    // Person Detection
    fun setDetectionModeOn(on : Boolean, distance : Float){
        getRobot()?.setDetectionModeOn(on, distance)
    }

    // Calls
    private fun callLibrarian(){
        val contacts = getRobot()?.allContact
        val librarianID = contacts?.find { it.name == "Johan" }?.userId
        if(librarianID == null){
            setBlockMode(false)
            speak("I couldn't find the librarian in the contact list")
            return
        }
        val participant = listOf(Participant(peerId = librarianID.toString(), platform = Platform.MOBILE))
        getRobot()?.startMeeting(participant, firstParticipantJoinedAsHost = false, blockRobotInteraction = true)
    }

    // Permissions
    private fun checkSelfPermission(permission: Permission) : Int? {
        return getRobot()?.checkSelfPermission(permission)
    }

    private fun requestPermissions(permissions: List<Permission>, requestCode: Int = 4){
        getRobot()?.requestPermissions(permissions, requestCode)
    }

    fun askRequiredPermissions(): Boolean {
        when {
            checkSelfPermission(Permission.SETTINGS) == 0 -> {
                setBlockMode(true)
                speak("Please allow the settings permission for the application to work properly")
                requestPermissions(listOf(Permission.SETTINGS))
                return false
            }
            checkSelfPermission(Permission.MAP) == 0 -> {
                setBlockMode(true)
                speak("Please allow the map permission for the application to work properly")
                requestPermissions(listOf(Permission.MAP))
                return false
            }
            checkSelfPermission(Permission.MEETINGS) == 0 -> {
                setBlockMode(true)
                speak("Please allow the meetings permission for the application to work properly")
                requestPermissions(listOf(Permission.MEETINGS))
                return false
            }
        }
        return true
    }


    // Personal interface and callbacks for robot initialization
    interface RobotReadyCallback {
        fun onRobotIsReady()
    }

    fun setRobotReadyCallback(callback: RobotReadyCallback) {
        this.readyCallback = callback
    }

    // Personal interface and callbacks for map loading
    interface MapReadyCallback {
        fun onMapIsReady()
    }

    fun setMapReadyCallback(callback: MapReadyCallback) {
        this.mapReadyCallback = callback
    }

    // Personal interface and callback for server requests
    interface RequestReadyCallback {
        fun onRequestIsReady(request: String)
    }

    fun setRequestReadyCallback(callback: RequestReadyCallback) {
        this.requestReadyCallback = callback
    }

    // Personal interface and callback for going back to patrol page
    interface BackToMainPageCallback {
        fun onBackToMainPage()
    }

    fun setBackToMainPageCallback(callback: BackToMainPageCallback) {
        this.backToMainPageCallback = callback
    }

    // Personal interface and callback for going back to go base page
    interface BackToBaseCallback {
        fun onBackToBase()
    }

    fun setBackToBaseCallback(callback: BackToBaseCallback) {
        this.backToBaseCallback = callback
    }

    // Personal interface and callback for when a meeting is started
    interface MeetingStartedCallback {
        fun onMeetingStarted()
    }

    fun setMeetingStartedCallback(callback: MeetingStartedCallback) {
        this.meetingStartedCallback = callback
    }

    // Inactivity handling
    private var inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {

        // If user not answering to ask satisfied request, turn it off and restart patrolling
        if(isAskSatisfiedRequest) {
            isAskSatisfiedRequest = false
            backToMainPageCallback?.onBackToMainPage()
            return@Runnable
        }

        // Do not trigger inactivity when on block mode or when going to a place
        if(!blockMode && !isMoveRequest){
            println("inactivity triggered")
            // Otherwise start patrolling again and
            patrol()
        }
    }

    fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, 20_000) // 20 seconds
    }

    // Speech handler to speak every x minutes
    private var speechHandler = Handler(Looper.getMainLooper())
    private val speechRunnable = Runnable {
        // Do not trigger speech when on block mode
        if(!blockMode){
            isDoNotEatSpeech = true
            speak("Please do not eat in the library. If you want to eat, go to the cafe. Thank you")
        }
    }

    fun startPeriodicSpeech(minutes: Int){
        speechHandler.removeCallbacks(speechRunnable)
        speechHandler.postDelayed(speechRunnable, minutes * 60 * 1000L)
    }

    // Robot SDK overrides
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        resetInactivityTimer()
        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            // If the robot was saying an answer, ask if satisfied then
            if(isAskSatisfiedRequest){
                askQuestion("Do you want me to call a librarian in case you're not satisfied with the answer ?")
                return
            }
            // If the robot talked due to periodical speech, restart it again
            if(isDoNotEatSpeech){
                isDoNotEatSpeech = false
                startPeriodicSpeech(15)
                return
            }

            // Otherwise don't change his current behavior
        }
    }

    override fun onDetectionStateChanged(state: Int) {
        // Ask question when detected and reset inactivity
        resetInactivityTimer()
        if (state == 2) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRequestTime < requestCooldownMillis) {
                return
            }
            lastRequestTime = currentTime

            getRobot()?.stopMovement()
            getRobot()?.setDetectionModeOn(false, 0.5f)
            getRobot()?.askQuestion("Hi, how can I help you ?")
        }
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        if(status == OnGoToLocationStatusChangedListener.COMPLETE){
            // When arriving at home base set into block mode
            if(location == "home base"){
                isMoveRequest = false
                setBlockMode(true)
                isAtHomeBase = true
                tiltHead(+55) // Make head go up
                backToMainPageCallback?.onBackToMainPage()
            }
            // When a move request, do not trigger the patrolling appending
            else if(isMoveRequest){
                isMoveRequest = false
                speak("We arrived")
                // Send temi back to home base after showing a location while being on home base
                if(isAtHomeBase){
                    goToHomeBase()
                    backToBaseCallback?.onBackToBase()
                }
            }
            // When arriving at a location when patrolling, append it to the list
            else {
                patrolStates.appendFirstPatrolLocation()
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

    override fun onLoadMapStatusChanged(status: Int, requestId: String) {
        when(status){
            1 -> {
                return
            }
            0 -> {
                val locationsWithoutHome = getRobot()?.locations?.filter{it.lowercase() != "home base"}
                if (locationsWithoutHome != null){
                    patrolStates = PatrolStates(
                        locationsWithoutHome,
                        locationsWithoutHome.associateWith { true }.toMutableMap()
                    )
                    mapReadyCallback?.onMapIsReady()
                }
            }
            else -> {
                mapReadyCallback?.onMapIsReady()
            }
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
            speak("Please restart the application after granting a new permission")
        }
    }

    override fun onTelepresenceStatusChanged(callState: CallState) {
        when(callState.state){
            CallState.State.ENDED -> {
                speak("I'm always in the library in case you need any help.")
                backToMainPageCallback?.onBackToMainPage()
            }
            CallState.State.DECLINED -> {
                speak("The librarian denied the call")
                backToMainPageCallback?.onBackToMainPage()
            }
            CallState.State.NOT_ANSWERED -> {
                speak("The librarian doesn't answer the call")
                backToMainPageCallback?.onBackToMainPage()
            }
            CallState.State.BUSY -> {
                speak("The librarian is busy")
                backToMainPageCallback?.onBackToMainPage()
            }
            CallState.State.POOR_CONNECTION -> {
                speak("Cannot establish the call due to connection issue")
                backToMainPageCallback?.onBackToMainPage()
            }
            CallState.State.CANT_JOIN -> {
                speak("Cannot join the call")
                backToMainPageCallback?.onBackToMainPage()
            }
            else -> {

            }
        }
    }

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        resetInactivityTimer()
        finishConversation()

        // Managing satisfied call request
        if(isAskSatisfiedRequest){
            isAskSatisfiedRequest = false
            if(isIntoList(asrResult, deniedKeywords) or !isIntoList(asrResult, approvedKeywords)){
                speak("OK. I'm always in the library in case you need any help.")
                backToMainPageCallback?.onBackToMainPage()
            }
            else {
                meetingStartedCallback?.onMeetingStarted()
                callLibrarian()
            }
        }

        // Go to locations
        else if (isIntoList(asrResult, keywords1_61, questions)){
            speak(answer_61)
            goTo("think space")
        }
        else if (isIntoList(asrResult, keywords1_62, questions)){
            speak(answer_62)
            goTo("dream space")
        }
        else if (isIntoList(asrResult, keywords1_63, questions)){
            speak(answer_63)
            goTo("idea space")
        }
        else if (isIntoList(asrResult, keywords1_64, questions)){
            speak(answer_64)
            goTo("smart learning hub")
        }
        else if (isIntoList(asrResult, keywords1_65, questions)){
            speak(answer_65)
            goTo("management collection")
        }
        else if (isIntoList(asrResult, keywords1_66, questions)){
            speak(answer_66)
            goTo("learn for life pod")
        }
        else if (isIntoList(asrResult, keywords1_67, questions)){
            speak(answer_67)
            goTo("dvds")
        }
        else if (isIntoList(asrResult, keywords1_68, questions)){
            speak(answer_68)
            goTo("smart kiosk")
        }
        else if (isIntoList(asrResult, keywords1_69, questions)){
            speak(answer_69)
            goTo("exhibition")
        }
        else if (isIntoList(asrResult, keywords1_70, questions)){
            speak(answer_70)
            goTo("book recommendations")
        }
        else if (isIntoList(asrResult, keywords1_71, questions)){
            speak(answer_71)
            goTo("magazines")
        }
        else if (isIntoList(asrResult, keywords1_72, questions)){
            speak(answer_72)
            goTo("lifestyle books")
        }
        else if (isIntoList(asrResult, keywords1_73, questions)){
            speak(answer_73)
            goTo("cafe")
        }
        else if (isIntoList(asrResult, keywords1_74, questions)){
            speak(answer_74)
            goTo("smart space")
        }
        else if (isIntoList(asrResult, keywords1_75, questions)){
            speak(answer_75)
            goTo("design collection")
        }
        else if (isIntoList(asrResult, keywords1_76, questions)){
            speak(answer_76)
            goTo("health sciences")
        }
        else if (isIntoList(asrResult, keywords1_77, questions)){
            speak(answer_77)
            goTo("life sciences collection")
        }
        else if (isIntoList(asrResult, keywords1_78, questions)){
            speak(answer_78)
            goTo("fiction books")
        }
        else if (isIntoList(asrResult, keywords1_79, questions)){
            speak(answer_79)
            goTo("project reports")
        }
        else if (isIntoList(asrResult, keywords1_80, questions)){
            speak(answer_80)
            goTo("photocopying stations")
        }
        else if (isIntoList(asrResult, keywords1_81, questions)){
            speak(answer_81)
            goTo("performance stage")
        }
        else if (isIntoList(asrResult, keywords1_82, questions)){
            speak(answer_82)
            goTo("lifestyle media")
        }
        else if (isIntoList(asrResult, keywords1_83, questions)){
            speak(answer_83)
            goTo("jason")
        }
        // Start chatbot request if not a goTo request
        else {
            isAskSatisfiedRequest = true
            requestReadyCallback?.onRequestIsReady(asrResult)
        }
    }
}