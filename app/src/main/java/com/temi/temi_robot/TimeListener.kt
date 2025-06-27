package com.temi.temi_robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

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

                // Going out of home base
                RobotController.setAtHomeBase(false)

                // Don't erase : avoids staying stuck for calculations
                RobotController.patrol()

                // Start main activity and open patrol page
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("fragment_to_open", "PatrolPage")
                }
                context.startActivity(launchIntent)
            }

            // Going back to home base on time slots ends
            "end" -> {
                RobotController.stopMovement()
                RobotController.setBlockMode(false)
                RobotController.goToHomeBase()

                // Change to go to base page
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("fragment_to_open", "GoToBasePage")
                }
                context.startActivity(launchIntent)

            }
            else -> {

            }
        }
    }
}