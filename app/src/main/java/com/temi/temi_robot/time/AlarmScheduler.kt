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

class AlarmScheduler(private var context: Context){

    private val timeSlotsMaxNumber = 3

    // Schedule a new alarm
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleTimeSlotAlarm(hour: Int, minute: Int, type: String, requestCode: Int, forTomorrow: Boolean=false) {
        println(type)
        println("planned alarm")
        val now = System.currentTimeMillis()

        val targetTime = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now || forTomorrow) {
                println("Schedule for tomorrow")
                // Alarm is in the past, schedule it for tomorrow
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val startIntent = Intent(context, TimeListener::class.java).apply {
            putExtra("type", type)
            putExtra("requestCode", requestCode)
            putExtra("timestamp", targetTime.timeInMillis)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetTime.timeInMillis,
            startPendingIntent
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
        for (i in 0 until 1000) {
            cancelAlarm(i)
        }

        // Plan an alarm for each time slot
        timeSlots.forEachIndexed { index, slot ->
            if(slot.getState()){
                scheduleTimeSlotAlarm(slot.getStartingHour().toInt(), slot.getStartingMinute().toInt(), "start", index)
                scheduleTimeSlotAlarm(slot.getEndingHour().toInt(), slot.getEndingMinute().toInt(), "end",  index+timeSlotsMaxNumber) // Offset between start and end alarms to avoid conflicts
            }
        }
    }

    fun getMaxTimeSlotsNumber(): Int {
        return timeSlotsMaxNumber
    }
}