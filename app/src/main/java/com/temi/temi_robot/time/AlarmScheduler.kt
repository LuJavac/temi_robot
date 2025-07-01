package com.temi.temi_robot.time

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.temi.temi_robot.time.TimeListener
import com.temi.temi_robot.dataclasses.TimeSlot
import java.util.Calendar

class AlarmScheduler(private var context: Context, private val timeSlotsMaxNumber: Int){

    // Schedule a new alarm
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleTimeSlotAlarm(slot: TimeSlot, requestCode: Int) {

        // Get android alarm planning service
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Converting time slot into Unix calendar unit time
        val startMillis = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, slot.getStartingHour().toInt())
            set(Calendar.MINUTE, slot.getStartingMinute().toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /// Alarm for start
        // Starting TimeListener service even when app not currently executed
        val startIntent = Intent(context, TimeListener::class.java).apply {
            putExtra("type", "start")
            putExtra("requestCode", requestCode)
            putExtra("timestamp", startMillis)
        }

        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        // Alarm for end
        val endMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, slot.getEndingHour().toInt())
            set(Calendar.MINUTE, slot.getEndingMinute().toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endIntent = Intent(context, TimeListener::class.java).apply {
            putExtra("type", "end")
            putExtra("requestCode", requestCode + 1000)// offset to avoid conflicts
            putExtra("timestamp", endMillis)
        }

        val endPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 1000,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            startMillis,
            startPendingIntent
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endMillis,
            endPendingIntent
        )
    }

    // Cancel an alarm
    fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, TimeListener::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun setAllAlarms(timeSlots: List<TimeSlot>){
        // Kill all alarms before setting new ones
        for (i in 0 until timeSlotsMaxNumber) {
            cancelAlarm(i)
            cancelAlarm(i + 1000)
        }

        // Plan an alarm for each time slot
        timeSlots.forEachIndexed { index, slot ->
            if(slot.getState()){
                scheduleTimeSlotAlarm(slot, index)
            }
        }
    }

    fun getMaxTimeSlotsNumber(): Int {
        return timeSlotsMaxNumber
    }

}