package com.temi.temi_robot.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.edit
import com.temi.temi_robot.MainActivity
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.pages.GoToBasePage

class TimeListener : BroadcastReceiver(){

    override fun onReceive(context: Context, intent: Intent) {

        // Check if added alarm is not in the past
        val tolerance = 1000L // 1 second tolerance
        val timestamp = intent.getLongExtra("timestamp", -1L)
        val now = System.currentTimeMillis()
        if (timestamp + tolerance < now) {
            println("return")
            return
        }

        // Wake up screen if turned off
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TemiApp::AlarmWakeLock"
        )
        wakeLock.acquire(3000L)

        // Identify if alarm is start or end or time slot
        val type = intent.getStringExtra("type")
        when (type) {
            // Start patrolling on time slots starts
            "start" -> {

                // To end any conversation going on before going to home base
                RobotController.finishConversation()
                RobotController.setSatisfiedRequest(false)
                RobotController.setMoveRequest(false)

                // Going out of home base
                RobotController.setAtHomeBase(false)

                RobotController.speak("Starting my daily jogging routine")

                // Don't erase : avoids staying stuck for calculations
                RobotController.patrol()

                // Start main activity and open patrol page
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("fragment_to_open", "MainPage")
                }
                context.startActivity(launchIntent)
            }

            // Going back to home base on time slots ends
            "end" -> {
                // To end any conversation going on before going to home base
                RobotController.finishConversation()
                RobotController.setSatisfiedRequest(false)
                RobotController.setMoveRequest(false)

                // Stop any patrolling
                RobotController.stopMovement()

                // Go to home base
                RobotController.speak("I finished my work bye bye")
                RobotController.goToHomeBase()

                // Restore GoToBase page when return to home base triggered during a call
                val prefs = context.getSharedPreferences("temi_state", Context.MODE_PRIVATE)
                prefs.edit {
                    putString("last_fragment", GoToBasePage::class.java.name)
                    putBoolean("should_restore_fragment", true)
                }

                // Change to go to base page
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("fragment_to_open", "GoToBasePage")
                }
                context.startActivity(launchIntent)

            }
            else -> {

            }
        }
    }
}