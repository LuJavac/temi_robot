package com.temi.temi_robot

import android.os.Handler
import android.os.Looper

import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnMovementStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.permission.Permission

class RobotController(private var defaultLocations: List<String>):
    Robot.AsrListener,
    Robot.TtsListener,
    OnRobotReadyListener,
    OnDetectionStateChangedListener,
    OnGoToLocationStatusChangedListener,
    OnMovementStatusChangedListener
{
    private val robot = Robot.getInstance() // Create robot object

    // Add listeners to robot instance
    init {
        robot.addAsrListener(this)
        robot.addTtsListener(this)
        robot.addOnRobotReadyListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnMovementStatusChangedListener(this)
    }

    // Lists for Q&A
    private val openingHours = listOf("time", "hours", "hour", "opened", "opening", "opens", "close", "closes", "closing")
    private val loansAndReturns = listOf("loan", "loans", "loaned", "loaning", "borrow", "borrowed", "borrowing", "return", "returns", "returning")
    private val bookingConditions = listOf("book", "bookings", "booking", "booked", "reservation", "reservations", "reserved")
    private val lossesCardAndStuff = listOf("lost", "loss", "losses")
    private val databases = listOf("databases", "database")
    private val examinationPapers = listOf("examination", "examination", "test papers", "model answers")
    private val journalsCollection = listOf("journal", "journals", "magazine", "magazines", "newspaper", "newspapers")


    private val loanRecords = listOf("check", "records", "record", "loan", "loans")
    private val renewLoans = listOf("renew", "renewed", "renewing", "loan", "loans")
    private val newMaterials = listOf("new", "materials", "material", "news", "borrow", "borrowed", "returned", "return", "overdue", "items", "item")
    private val payFines = listOf("pay", "fines", "fine", "fees", "fee", "paid", "lost", "materials", "material")
    private val accessEressources = listOf("access", "resources", "resource", "who", "off", "campus")
    private val libraryOfThings = listOf("library", "things", "thing", "Library of Things")
    private val bookRooms = listOf("book", "rooms", "room")
    private val contactAssistance = listOf("contact", "call", "librarian", "assistance", "assistant")
    private val location = listOf("located", "where", "NYP", "location", "library")
    private val access = listOf("who", "access", "library")
    private val holidays = listOf("holidays", "holiday", "closed", "close", "open", "opened", "term", "breaks", "break")
    private val contact = listOf("contact", "library")
    private val virtualTour = listOf("virtual", "virtuals", "tour", "tours")
    private val visitors = listOf("visitor", "visitors", "walk-in", "exterior", "public")
    private val lostSomething = listOf("lost", "something", "loose")
    private val food = listOf("food", "foods", "drink", "drinks")
    private val zones = listOf("quiet", "zones", "zone", "discussion", "group", "area", "areas")
    private val register = listOf("register", "registration", "registered", "member")
    private val borrow = listOf("staff", "borrow", "items", "material", "materials")
    private val alumni = listOf("alumni", "alumnis")
    private val forgetCard = listOf("forget", "card", "cards", "student", "staff")
    private val digitalAccess = listOf("digital", "digitals", "ressources", "ressource")
    private val nbItems = listOf("number", "items", "item", "borrow", "at one time")
    private val loanDurations = listOf("durations", "duration")
    private val renewBorrowedItem = listOf("renew", "borrowed", "item", "items")
    private val returnBorrowedItem = listOf("return", "borrowed", "item", "items")
    private val returnLate = listOf("happens", "happened", "late")
    private val overdue = listOf("fines", "fine", "overdue", "books", "media")
    private val currently = listOf("borrow", "borrowed", "currently", "already")
    private val history = listOf("history", "histories", "history")
    private val otherLibraries = listOf("libraries", "library", "other", "Singapore")
    private val damage = listOf("damage", "damaged", "damaged")
    private val eBooks = listOf("e-books", "ebook", "ebooks","e-journals", "e-journal", "ejournal")
    private val search = listOf("search", "searching", "find", "finds", "finds", "online", "ressources")
    private val download = listOf("download", "downloads", "download")
    private val IEEE = listOf("IEEE", "ieee", "JSTOR", "jstor")
    private val exams = listOf("exams", "exam", "examination", "test", "tests", "past-year", "last year")
    private val portal = listOf("portal", "portals")
    private val app = listOf("app", "apps", "application", "applications")
    private val bookRoom = listOf("book", "room", "rooms", "discussion room")
    private val print = listOf("print", "prints", "print", "printing", "photocopy", "photocopies", "photocopy", "photocopying")
    private val laptop = listOf("laptop", "laptops")
    private val wifi = listOf("wifi", "internet", "wi-fi")
    private val charge = listOf("charge", "charging", "charger")
    private val research = listOf("research", "researches", "research")
    private val citation = listOf("citation", "citations", "reference", "references", "referencing")
    private val workshops = listOf("workshops", "workshop", "seminar", "seminars", "training sessions", "training session")
    private val consultation = listOf("consultation", "consultations", "consultant", "consultants")
    private val guides = listOf("guides", "guide", "guidebook", "guidebooks")
    private val turnitin = listOf("turnitin", "turnitins")
    private val archives = listOf("archives", "archive", "special collections", "special collection")
    private val recommend = listOf("recommend", "recommendations", "recommendation")
    private val feedback = listOf("feedback", "feedbacks")
    private val support = listOf("support", "supports", "special needs")


    private val jason = listOf("jason", "jackson")

    // Time values
    private var lastRequestTime = 0L //
    private val requestCooldownMillis = 20000L // 50 seconds

    // Inactivity handling
    private var inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        patrol(defaultLocations)
        robot.setDetectionModeOn(true, 0.5f)
    }

    fun resetInactivityTimer() {
        println("reset")
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, 20_000) // 20 seconds
    }

    // Own variables
    private var isMoveRequest = false
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

    // Own interface and callbacks
    interface RobotReadyCallback {
        fun onRobotIsReady()
    }

    fun setRobotReadyCallback(callback: RobotReadyCallback) {
        this.readyCallback = callback
    }

    // Overrides
    override fun onDetectionStateChanged(state: Int) {
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

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        resetInactivityTimer()
        if (isMoveRequest) {
            return
        }

        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            patrol(defaultLocations)
            robot.setDetectionModeOn(true, 0.5f)
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

    override fun onMovementStatusChanged(type: String, status: String) {
        println("status changed")
        resetInactivityTimer()
    }

    override fun onRobotReady(isReady: Boolean) {
        if(isReady){
            readyCallback?.onRobotIsReady()
        }
    }
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        resetInactivityTimer()
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
            isMoveRequest = true
            speak( "Let's go see this weirdo")
            robot.goTo("jason")
        }
        else if(loanRecords.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("You can check your loan account by logging in to the library portal using your NYP email address.")
        }
        else if(renewLoans.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Loans can be renewed at the Self-Check Machines, at the Information Services Counter, or via Library Portal.")
        }
        else if(newMaterials.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("No. You are not allowed to borrow any Library materials unless the overdue items are returned and fines are cleared.")
        }
        else if(payFines.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Cashless payment is implemented for all payment of fines and lost materials. You can pay by NETS, PayNow or via AXS.")
        }
        else if(accessEressources.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Off-campus access to these e-resources is available to NYP students & full-time staff.")
        }
        else if(libraryOfThings.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Library of Things is a collection of cool gadgets and educational kits that can help to enhance your learning experiences. They include board games, robotic kits and other learning tools.")
        }
        else if(bookRooms.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("A minimum of 3 students can book a library room for 2 hours. With 5 days advanced booking, you can book via the Library or Student portal.")
        }
        else if(contactAssistance.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Please call the library hotline at 65500150")
        }
        else if(location.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("The library is located at Block A, Level 4, Nanyang Polytechnic campus .")
        }
        else if(access.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("NYP students, staff, and registered members such as alumni and partners can access the library.")
        }
        else if(holidays.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("The library is open during term breaks but closed on public holidays. Check the calendar on the library website for holiday hours .")
        }
        else if(contact.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("You can email the library at [library@nyp.edu.sg](mailto:library@nyp.edu.sg) or call the helpdesk at +65 6451 5115.")
        }
        else if(virtualTour.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, a virtual tour is available on the library’s website to help new users explore the space.")
        }
        else if(visitors.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Access is limited to NYP members. External visitors may need prior approval or appointment.")
        }
        else if(lostSomething.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Approach the service desk or contact NYP's Lost and Found at the Student Affairs Office.")
        }
        else if(food.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Only bottled drinks are allowed. Food is strictly prohibited to maintain cleanliness.")
        }
        else if(zones.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, the library has designated quiet study areas and group discussion rooms that can be booked online.")
        }
        else if(register.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("NYP students and staff are automatically registered. Alumni and external users can apply via the library portal .")
        }
        else if(alumni.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, alumni may access selected services upon registration. Contact the library for details .")
        }
        else if(borrow.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, staff enjoy extended loan periods and access to all resources .")
        }
        else if(forgetCard.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Visit the service counter with valid identification for assistance.")
        }
        else if(digitalAccess.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Use your NYP network ID to log into the library portal for remote access to e-resources .")
        }
        else if(nbItems.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Students may borrow up to 8 items. Staff may borrow up to 15 items .")
        }
        else if(loanDurations.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Most books have a 14-day loan period, with auto-renewal unless recalled .")
        }
        else if(renewBorrowedItem.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Renewal is automatic if the item is not reserved by another user. Manual renewal is also possible via the library portal .")
        }
        else if(returnBorrowedItem.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Use the book drop near the library entrance or return items at the service counter .")
        }
        else if(returnLate.any { word -> asrResult.contains(word, ignoreCase = true)}) {
            robot.finishConversation()
            speak("Late returns incurs a fine. Check the fine policy on the library portal .")
        }
        else if(overdue.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Fines typically start from \$0.50 per day for books and \$1.00 per hour/day for media. Refer to the fines section online .")
        }
        else if(currently.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, place a reservation online using the library catalogue .")
        }
        else if(history.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Log in to your library account to view loan history and current items .")
        }
        else if(otherLibraries.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("No, materials borrowed from NYP Library must be returned to NYP .")
        }
        else if(damage.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Report it immediately. You may be charged a replacement fee and administrative cost .")
        }
        else if(eBooks.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Log into the library portal and search the online catalogue or databases section.")
        }
        else if(search.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Use the OneSearch feature on the library homepage for books, articles, and media .")
        }
        else if(download.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, these are accessible through the NYP Library’s subscribed databases .")
        }
        else if(IEEE.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, these are accessible through the NYP Library’s subscribed databases .")
        }
        else if(exams.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, they are available under the Digital Repository or through your school’s LMS .")
        }
        else if(portal.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Use your NYP network ID and password to log in at lib.nyp.edu.sg .")
        }
        else if(app.any { word -> asrResult.contains(word, ignoreCase = true)}) {
            robot.finishConversation()
            speak("No dedicated app, but the website is mobile-friendly and supports access to all services .")
        }
        else if(bookRoom.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Use the online booking system via the library portal.")
        }
        else if(print.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, you can print, copy, and scan using your student card at various stations .")
        }
        else if(laptop.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Install the NYP network printer service or use WebPrint via the NYP intranet .")
        }
        else if(wifi.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, connect to the NYP wireless network using your credentials .")
        }
        else if(charge.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, charging points are available at designated study tables .")
        }
        else if(research.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, librarians are available for one-on-one research consultations .")
        }
        else if(citation.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, citation guides (APA, MLA, etc.) are available, and librarians can assist .")
        }
        else if(workshops.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, workshops on research skills, information literacy, and citation tools are offered each semester .")
        }
        else if(consultation.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, schedule a session via email or the library portal .")
        }
        else if(guides.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, online guides and video tutorials are available under “Research Help” on the library website .")
        }
        else if(turnitin.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Turnitin access is managed through NYP’s LMS, not directly via the library.")
        }
        else if(archives.any { word -> asrResult.contains(word, ignoreCase = true)}) {
            robot.finishConversation()
            speak("Yes, the library holds NYP publications, project reports, and special reference materials.")
        }
        else if(recommend.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, use the “Recommend a Title” form available on the library site.")
        }
        else if(feedback.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Submit feedback via the online form or speak to staff at the service desk.")
        }
        else if(support.any { word -> asrResult.contains(word, ignoreCase = true)}){
            robot.finishConversation()
            speak("Yes, assistive technologies and tailored services are available. Contact the library for more information.")
        }
        else {
            robot.finishConversation()
            speak("I didn't understand that")
        }
    }
}