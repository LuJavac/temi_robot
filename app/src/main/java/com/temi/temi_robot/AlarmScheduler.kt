package com.temi.temi_robot

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.temi.temi_robot.dataclasses.TimeSlot
import java.util.Calendar

class AlarmScheduler(private var context: Context){

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleTimeSlotAlarm(slot: TimeSlot, requestCode: Int) {

        // Get android alarm planning service
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Starting TimeListener service even when app not currently executed
        val intent = Intent(context, TimeListener::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Converting time slot into Unix calendar unit time
        val startMillis = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, slot.getStartingHour().toInt())
            set(Calendar.MINUTE, slot.getStartingMinute().toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Check if time slot start time is not in the past. If in the past don't do anything.
        if (startMillis > System.currentTimeMillis()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                startMillis,
                pendingIntent
            )
        }
    }
}