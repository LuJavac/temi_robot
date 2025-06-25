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
    private val questions = listOf("where", "go", "take me", "find")

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


    // Time values
    private var lastRequestTime = 0L //
    private val requestCooldownMillis = 20000L // 20 seconds

    // Inactivity handling
    private var inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        println(blockMode)
        println(isAskSatisfiedRequest)
        println(isMoveRequest)
        if(!blockMode){
            println("inactivity triggered")
            patrol()
            getRobot()?.setDetectionModeOn(true, 0.5f)
        }
        if(isAskSatisfiedRequest) {
            isAskSatisfiedRequest = false
            backToPatrolCallback?.onBackToPatrol()
        }
    }

    fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, 20_000) // 20 seconds
    }

    // Own variables
    private var isMoveRequest = false
    private var blockMode = false
    private var isAskSatisfiedRequest = false

    private var readyCallback: RobotReadyCallback? = null
    private var mapReadyCallback: MapReadyCallback? = null
    private var requestReadyCallback: RequestReadyCallback? = null
    private var backToPatrolCallback: BackToPatrolCallback? = null
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

    fun setLastRequestTimeNow(){
        lastRequestTime = System.currentTimeMillis()
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
        isMoveRequest = true
        getRobot()?.goTo(location)
    }

    fun patrol(){
        getRobot()?.patrol(patrolStates.getPatrolLocations(), times = 0)
    }

    fun stopMovement(){
        getRobot()?.stopMovement()
    }

    fun goToHomeBase(){
        getRobot()?.goTo("home base")
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
    interface BackToPatrolCallback {
        fun onBackToPatrol()
    }

    fun setBackToPatrolCallback(callback: BackToPatrolCallback) {
        this.backToPatrolCallback = callback
    }

    // Personal interface and callback for when a meeting is started
    interface MeetingStartedCallback {
        fun onMeetingStarted()
    }

    fun setMeetingStartedCallback(callback: MeetingStartedCallback) {
        this.meetingStartedCallback = callback
    }

    // Robot SDK overrides
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        resetInactivityTimer()
        if (isMoveRequest || blockMode) {
            return
        }

        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            if(isAskSatisfiedRequest){
                askQuestion("Do you want me to call a librarian in case you're not satisfied with the answer ?")
                return
            }
            patrol()
            getRobot()?.setDetectionModeOn(true, 0.5f)
        }
    }

    override fun onDetectionStateChanged(state: Int) {
        if(isMoveRequest || blockMode){
            return
        }
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
            if(location == "home base"){
                println("home base reached")
                isMoveRequest = false
                setBlockMode(true)
            }
            if(isMoveRequest){
                isMoveRequest = false
                speak("We arrived")
            } else {
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
            speak("please restart the application after granting a new permission")
        }
    }

    override fun onTelepresenceStatusChanged(callState: CallState) {
        when(callState.state){
            CallState.State.ENDED -> {
                setBlockMode(false)
                speak("I'm always in the library in case you need any help.")
                backToPatrolCallback?.onBackToPatrol()
            }
            CallState.State.DECLINED -> {
                setBlockMode(false)
                speak("The librarian denied the call")
                backToPatrolCallback?.onBackToPatrol()
            }
            CallState.State.NOT_ANSWERED -> {
                setBlockMode(false)
                speak("The librarian doesn't answer the call")
                backToPatrolCallback?.onBackToPatrol()
            }
            CallState.State.BUSY -> {
                setBlockMode(false)
                speak("The librarian is busy")
                backToPatrolCallback?.onBackToPatrol()
            }
            CallState.State.POOR_CONNECTION -> {
                setBlockMode(false)
                speak("Cannot establish the call due to connection issue")
                backToPatrolCallback?.onBackToPatrol()
            }
            CallState.State.CANT_JOIN -> {
                setBlockMode(false)
                speak("Cannot join the call")
                backToPatrolCallback?.onBackToPatrol()
            }
            else -> {

            }
        }
    }

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        resetInactivityTimer()

        // Managing satisfied call request
        if(isAskSatisfiedRequest){
            getRobot()?.finishConversation()
            isAskSatisfiedRequest = false
            if(isIntoList(asrResult, deniedKeywords) or !isIntoList(asrResult, approvedKeywords)){
                speak("OK. I'm always in the library in case you need any help.")
                backToPatrolCallback?.onBackToPatrol()
            }
            else {
                setBlockMode(true)
                meetingStartedCallback?.onMeetingStarted()
                callLibrarian()
            }
        }

        // Go to locations
        else if (isIntoList(asrResult, keywords1_61, questions)){
            getRobot()?.finishConversation()
            speak(answer_61)
            goTo("think space")
        }
        else if (isIntoList(asrResult, keywords1_62, questions)){
            getRobot()?.finishConversation()
            speak(answer_62)
            goTo("dream space")
        }
        else if (isIntoList(asrResult, keywords1_63, questions)){
            getRobot()?.finishConversation()
            speak(answer_63)
            goTo("idea space")
        }
        else if (isIntoList(asrResult, keywords1_64, questions)){
            getRobot()?.finishConversation()
            speak(answer_64)
            goTo("smart learning hub")
        }
        else if (isIntoList(asrResult, keywords1_65, questions)){
            getRobot()?.finishConversation()
            speak(answer_65)
            goTo("management collection")
        }
        else if (isIntoList(asrResult, keywords1_66, questions)){
            getRobot()?.finishConversation()
            speak(answer_66)
            goTo("learn for life pod")
        }
        else if (isIntoList(asrResult, keywords1_67, questions)){
            getRobot()?.finishConversation()
            speak(answer_67)
            goTo("dvds")
        }
        else if (isIntoList(asrResult, keywords1_68, questions)){
            getRobot()?.finishConversation()
            speak(answer_68)
            goTo("smart kiosk")
        }
        else if (isIntoList(asrResult, keywords1_69, questions)){
            getRobot()?.finishConversation()
            speak(answer_69)
            goTo("exhibition")
        }
        else if (isIntoList(asrResult, keywords1_70, questions)){
            getRobot()?.finishConversation()
            speak(answer_70)
            goTo("book recommendations")
        }
        else if (isIntoList(asrResult, keywords1_71, questions)){
            getRobot()?.finishConversation()
            speak(answer_71)
            goTo("magazines")
        }
        else if (isIntoList(asrResult, keywords1_72, questions)){
            getRobot()?.finishConversation()
            speak(answer_72)
            goTo("lifestyle books")
        }
        else if (isIntoList(asrResult, keywords1_73, questions)){
            getRobot()?.finishConversation()
            speak(answer_73)
            goTo("cafe")
        }
        else if (isIntoList(asrResult, keywords1_74, questions)){
            getRobot()?.finishConversation()
            speak(answer_74)
            goTo("smart space")
        }
        else if (isIntoList(asrResult, keywords1_75, questions)){
            getRobot()?.finishConversation()
            speak(answer_75)
            goTo("design collection")
        }
        else if (isIntoList(asrResult, keywords1_76, questions)){
            getRobot()?.finishConversation()
            speak(answer_76)
            goTo("health sciences")
        }
        else if (isIntoList(asrResult, keywords1_77, questions)){
            getRobot()?.finishConversation()
            speak(answer_77)
            goTo("life sciences collection")
        }
        else if (isIntoList(asrResult, keywords1_78, questions)){
            getRobot()?.finishConversation()
            speak(answer_78)
            goTo("fiction books")
        }
        else if (isIntoList(asrResult, keywords1_79, questions)){
            getRobot()?.finishConversation()
            speak(answer_79)
            goTo("project reports")
        }
        else if (isIntoList(asrResult, keywords1_80, questions)){
            getRobot()?.finishConversation()
            speak(answer_80)
            goTo("photocopying stations")
        }
        else if (isIntoList(asrResult, keywords1_81, questions)){
            getRobot()?.finishConversation()
            speak(answer_81)
            goTo("performance stage")
        }
        else if (isIntoList(asrResult, keywords1_82, questions)){
            getRobot()?.finishConversation()
            speak(answer_82)
            goTo("lifestyle media")
        }

        else {
            getRobot()?.finishConversation()
            isAskSatisfiedRequest = true
            requestReadyCallback?.onRequestIsReady(asrResult)
        }
    }
}