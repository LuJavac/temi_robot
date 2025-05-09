package com.temi.temi_robot

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.constants.CliffSensorMode
import com.robotemi.sdk.constants.HardButton
import com.robotemi.sdk.constants.SensitivityLevel
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener
import com.robotemi.sdk.listeners.OnConversationStatusChangedListener
import com.robotemi.sdk.listeners.OnDetectionDataChangedListener
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnMovementStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotDragStateChangedListener
import com.robotemi.sdk.listeners.OnRobotLiftedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.listeners.OnTtsVisualizerWaveFormDataChangedListener
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.model.DetectionData
import com.robotemi.sdk.navigation.model.Position
import com.robotemi.sdk.navigation.model.SpeedLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Data classes
data class WakeUp(
    val result: String
)

class RobotController():
    Robot.WakeupWordListener
{
    private val robot = Robot.getInstance() // Create robot object

    // Add listeners to robot instance
    init {
        robot.addWakeupWordListener(this)
    }

    // Stateflows for listeners
    private val _wakeUp = MutableStateFlow(WakeUp("hello"))
    val wakeUp = _wakeUp.asStateFlow()


    // General functions
    fun speak(speech: String, haveFace: Boolean = true) {

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


    fun wakeUp() {
        robot.wakeup(listOf(SttLanguage.SYSTEM))
    }

    // Overrides
    override fun onWakeupWord(wakeupWord: String, direction: Int) {
        _wakeUp.update {
            WakeUp(wakeupWord)
        }
    }




}