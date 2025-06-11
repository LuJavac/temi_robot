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

// Robot control class
class RobotController(private var mapName: String):
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
    private val robot = Robot.getInstance() // Create robot object
    private var locations = emptyList<String>()

    // Add listeners to robot instance
    init {
        robot.addAsrListener(this)
        robot.addTtsListener(this)
        robot.addOnRobotReadyListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnDistanceToDestinationChangedListener(this)
        robot.addOnRequestPermissionResultListener(this)
        robot.addOnLoadMapStatusChangedListener(this)
        robot.addOnTelepresenceStatusChangedListener(this)
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
    private val requestCooldownMillis = 20000L // 20 seconds

    // Inactivity handling
    private var inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if(!blockMode){
            patrol(locations)
            robot.setDetectionModeOn(true, 0.5f)
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

    /////////// General functions

    // Getters and setters
    fun setBlockMode(value: Boolean) {
        blockMode = value
        setDetectionModeOn(!value, 0.5f)
    }

    fun getLocations() : List<String> {
        return locations
    }

    fun setLocations(newLocations: List<String>) {
        locations = newLocations
    }

    // Utility functions
    private fun changeLocationsOrder() {
        locations = locations.drop(1) + locations.first()
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
    fun loadMap() {
        val maps = robot.getMapList()
        val map = maps.find { it.name == mapName }
        if(map == null){
            locations = emptyList()
            readyCallback?.onRobotIsReady()
        }
        else {
            robot.loadMap(map.id)
        }
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

    // Calls
    fun callLibrarian(){
        val contacts = robot.allContact
        val librarianID = contacts.find { it.name == "Johan" }?.userId
        if(librarianID == null){
            setBlockMode(false)
            speak("I couldn't find the librarian in the contact list")
            return
        }
        val participant = listOf(Participant(peerId = librarianID.toString(), platform = Platform.MOBILE))
        robot.startMeeting(participant, firstParticipantJoinedAsHost = true, blockRobotInteraction = false)
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
            patrol(locations)
            robot.setDetectionModeOn(true, 0.5f)
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

    override fun onLoadMapStatusChanged(status: Int, requestId: String) {
        when(status){
            1 -> {
                return
            }
            0 -> {
                locations = robot.locations
                mapReadyCallback?.onMapIsReady()
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
            }
            CallState.State.NOT_ANSWERED -> {
                setBlockMode(false)
                speak("The librarian doesn't answer the call")
            }
            CallState.State.BUSY -> {
                setBlockMode(false)
                speak("The librarian is busy")
            }
            CallState.State.POOR_CONNECTION -> {
                setBlockMode(false)
                speak("Cannot establish the call due to connection issue")
            }
            CallState.State.CANT_JOIN -> {
                setBlockMode(false)
                speak("Cannot join the call")
            }
            else -> {

            }
        }
    }

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        resetInactivityTimer()
        if(isAskSatisfiedRequest){
            robot.finishConversation()
            isAskSatisfiedRequest = false
            if(isIntoList(asrResult, deniedKeywords) or !isIntoList(asrResult, approvedKeywords)){
                speak("OK. I'm always in the library in case you need any help.")
                backToPatrolCallback?.onBackToPatrol()
            }
            else {
                setBlockMode(true)
                callLibrarian()
            }
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
            isAskSatisfiedRequest = true
            requestReadyCallback?.onRequestIsReady(asrResult)
        }
    }
}