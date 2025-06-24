package com.temi.temi_robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimeListener : BroadcastReceiver(){

    override fun onReceive(context: Context, intent: Intent) {
        val tolerance = 1000L // 1 second tolerance
        val type = intent.getStringExtra("type")
        val timestamp = intent.getLongExtra("timestamp", -1L)

        val now = System.currentTimeMillis()

        if (timestamp + tolerance < now) {
            println("return")
            return
        }

        when (type) {
            "start" -> {
                RobotController.setBlockMode(false)
                RobotController.patrol()
                println("start patrol")
            }
            "end" -> {
                RobotController.goToHomeBase()
                println("home base")
            }
            else -> {

            }
        }
    }
}