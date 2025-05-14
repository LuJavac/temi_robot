package com.temi.temi_robot


import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.permission.Permission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


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

    // Lists for Q&A
    private val openingHours = listOf("time", "hours", "hour", "opened", "opening", "opens", "close", "closes", "closing")
    private val loansAndReturns = listOf("loan", "loans", "loaned", "loaning", "borrow", "borrowed", "borrowing", "return", "returns", "returning")
    private val bookingConditions = listOf("book", "bookings", "booking", "booked", "reservation", "reservations", "reserved")
    private val lossesCardAndStuff = listOf("lost", "loss", "losses")
    private val databases = listOf("databases", "database")
    private val examinationPapers = listOf("examination", "examination", "test papers", "model answers")
    private val journalsCollection = listOf("journal", "journals", "magazine", "magazines", "newspaper", "newspapers")
    private val jason = listOf("jason", "jackson")


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

    fun askQuestion(question: String) {
        robot.askQuestion(question)
    }

    fun getLocations() : List<String> {
        return robot.locations
    }

    fun patrol(locations : List<String>){
        robot.patrol(locations, times = 0)
    }

    // Permissions
    fun checkSelfPermission(permission: Permission) : Int{
        return robot.checkSelfPermission(permission)
    }

    fun requestPermissions(permissions: List<Permission>, requestCode: Int = 4){
        robot.requestPermissions(permissions, requestCode)
    }

    // Overrides
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {

        if(openingHours.any { word -> asrResult.contains(word, ignoreCase = true)}){ // Checks if one of the key words is part of the demand
            robot.finishConversation()
            speak("The main library is opened from 8:30 am to 8 pm from Monday to Friday, and is closed on Saturdays. The reading lounge is opened from 8:30 am to 9 pm from Monday to Friday, and from 8:30 am to 1 pm on Saturdays. On Sundays and public holidays, everything is closed.")
        }
        else if(loansAndReturns.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("You can borrow or return all accompanying materials at the Information Services Counter on Level 4. However, you cannot borrow new library materials if you haven't returned overdue items. You can renew your loans at the Self-Check Machines at the Information Services Counter on level 4, or via the internet library portal. It is all free of charges. To check your loan records, you can use the internet library portal.")
        }
        else if(bookingConditions.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("To manage your bookings, please use the internet library portal. Reservations are free of charge, and can only be placed on items with status \"on loan\" and \"in processed\". Others status cannot be reserved. Reserved items must be collected by the person who booked them.")
        }
        else if(lossesCardAndStuff.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("For any questions in case you've lost your library card or any borrowed item, please report to the Information Services Counter, at level 4.")
        }
        else if(databases.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("To access online databases, you can connect via the internet library portal and go to \"Resources\". These are available to all NYP students and full-time staff.")
        }
        else if(examinationPapers.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("To access past examination papers, you must connect via the internet library portal. Electronic copies from academic year 2015 onwards are available in PDF. You can access, download and print them. However, common test papers and model answers to examination papers are not available.")
        }
        else if(journalsCollection.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak( "The journals are located at different places. The lifestyles magazines are at level 4, and the health, sciences and academic journals are at level 5.")
        }
        else if(jason.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak( "Let's go see this weirdo")
            robot.goTo("jason")
        }

        else {
            robot.startDefaultNlu(asrResult, SttLanguage.EN_US)
        }

    }
}