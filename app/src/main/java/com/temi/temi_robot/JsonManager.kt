package com.temi.temi_robot

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

object JsonManager {

    // Restore data from file
    inline fun <reified T> restoreFromFile(context: Context, fileName: String): T? {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) {
            try {
                val json = file.readText()
                Json.Default.decodeFromString<T>(json)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    // Write data to file
    inline fun <reified T> writeToFile(context: Context, data: T, fileName: String) {
        val json = Json.Default.encodeToString(serializer(), data)
        File(context.filesDir, fileName).writeText(json)
    }

    // Delete Json file :: FOR TESTING PURPOSES ONLY
    private fun deleteFile(context: Context, fileName: String) {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

}