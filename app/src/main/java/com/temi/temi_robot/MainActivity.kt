package com.temi.temi_robot

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.temi.temi_robot.pages.MainPage
import androidx.core.content.edit
import com.robotemi.sdk.Robot
import com.temi.temi_robot.pages.FirstPage
import com.temi.temi_robot.pages.GoToBasePage
import com.temi.temi_robot.telemetry.TelemetryClient
import com.temi.temi_robot.time.AlarmScheduler
import com.temi.temi_robot.time.TimeListener

class MainActivity : AppCompatActivity() {
    // Carte par défaut (celle de NYP RIG). Sur un robot qui ne la possède pas
    // (ex: NYP BOA), RobotController.loadMap() bascule automatiquement sur la
    // carte actuellement active du robot.
    private val mapName = "R4 Block Complete (USE THIS) for RIG1"
    internal var savePatrolStatesFileName = "patrolState.json"
    internal var saveTimeSlotsFileName = "timeSlots.json"

    // IP DU RASPBERRY PI
    internal var serverUrl = "http://192.168.1.8:5000/process"
    internal var streamUrl = "http://192.168.1.8:5000/stream"

    internal var userRequest : String? = null

    internal lateinit var connectivityManager: ConnectivityManager
    internal lateinit var alarmScheduler: AlarmScheduler
    internal lateinit var timeListener: TimeListener

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main_activity)

        setTurnScreenOn(true)
        setShowWhenLocked(true)

        RobotController.setRobot(Robot.getInstance())
        RobotController.setMapName(mapName)
        RobotController.setListeners()
        RobotController.initAndroidTts(this)
        TelemetryClient.init(this)

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        alarmScheduler = AlarmScheduler(this)
        timeListener = TimeListener()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FirstPage())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("temi_state", MODE_PRIVATE)
        val shouldRestore = prefs.getBoolean("should_restore_fragment", false)
        val fragmentName = prefs.getString("last_fragment", null)

        if (shouldRestore && fragmentName != null) {
            try {
                val fragmentClass = Class.forName(fragmentName).asSubclass(Fragment::class.java)
                val fragment = fragmentClass.getDeclaredConstructor().newInstance()

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()

            } catch (e: Exception) {
                e.printStackTrace()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MainPage())
                    .commit()
            }
        }
        prefs.edit { putBoolean("should_restore_fragment", false) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("fragment_to_open")?.let {
            when (it) {
                "MainPage" -> {
                    val mainPage = MainPage()
                    val args = Bundle()
                    args.putString("notPatrolAgain", "true")
                    mainPage.arguments = args

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, mainPage)
                        .commit()
                }
                "GoToBasePage" -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, GoToBasePage())
                        .commit()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RobotController.releaseAndroidTts()
        getSharedPreferences("temi_state", MODE_PRIVATE).edit {
            putBoolean("should_restore_fragment", false)
                .remove("last_fragment")
        }
    }
}