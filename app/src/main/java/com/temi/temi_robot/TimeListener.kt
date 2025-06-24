package com.temi.temi_robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimeListener : BroadcastReceiver(){

    override fun onReceive(context: Context, intent: Intent) {
        // Tu peux exécuter n'importe quelle logique ici
        RobotController.speak("hello")
        // Exemple : appeler une fonction du robot, démarrer un service, etc.
    }
}