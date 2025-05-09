package com.temi.temi_robot

import android.widget.Toast
import com.robotemi.sdk.NlpResult
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.constants.Gender
import com.robotemi.sdk.voice.model.TtsVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


// Data classes
data class AskResult(val result: String, val id: Long = System.currentTimeMillis())

class RobotController():
    Robot.AsrListener
{
    private val robot = Robot.getInstance() // Create robot object

    // Add listeners to robot instance
    init {
        robot.addAsrListener(this)
    }

    // Stateflows for listeners
    private val _askResult = MutableStateFlow(AskResult("hello"))
    val askResult = _askResult.asStateFlow()


    // General functions
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

    fun setTtsVoice(gender: Gender, speed: Float, pitch: Int){ // Setting voice parameters
        // Define voice settings
        val voiceSettings = TtsVoice(gender, speed, pitch)
        // set the voice
        robot.setTtsVoice(voiceSettings)
    }

    fun askQuestion(question: String) {
        robot.askQuestion(question)
    }

    fun finishConversation(){
        robot.finishConversation()
    }

    // Overrides
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        when {
            asrResult.equals("Hello", ignoreCase = true) -> {
                robot.askQuestion("Hello, I'm temi, what can I do for you?")
            }
            asrResult.equals("Play music", ignoreCase = true) -> {
                robot.finishConversation()
                speak("Okay, please enjoy.")
            }
            asrResult.equals("Play movie", ignoreCase = true) -> {
                robot.finishConversation()
                speak("Okay, please enjoy.")
            }
            asrResult.lowercase().contains("follow me") -> {
                robot.finishConversation()
                speak("Okay, please enjoy.")
            }
            asrResult.lowercase().contains("go to home base") -> {
                robot.finishConversation()
                speak("Okay, please enjoy.")
            }
            else -> {
                robot.askQuestion("Sorry I can't understand you, could you please ask something else?")
            }
        }
    }
}