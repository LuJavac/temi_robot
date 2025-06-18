package com.temi.temi_robot


import android.net.ConnectivityManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.temi.temi_robot.pages.FirstPage
import com.temi.temi_robot.pages.PatrolPage
import androidx.core.content.edit

// Activity class
class MainActivity : AppCompatActivity() {
    private val mapName = "R4 Block Complete (USE THIS) for BOA1" //level 2 backup
    internal var savePatrolStatesFileName = "patrolState.json"

    internal var robotController: RobotController = RobotController(mapName)
    internal var userRequest : String? = null

    internal lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main_activity)

        // Manages Wi-Fi connectivity detection
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Load SettingsPage as default
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FirstPage())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()

        // Getting last fragment saved and if needed to load it
        val prefs = getSharedPreferences("temi_state", MODE_PRIVATE)
        val shouldRestore = prefs.getBoolean("should_restore_fragment", false)
        val fragmentName = prefs.getString("last_fragment", null)

        // If needed, load the last fragment saved
        if (shouldRestore && fragmentName != null) {
            try {
                val fragmentClass = Class.forName(fragmentName).asSubclass(Fragment::class.java)
                val fragment = fragmentClass.getDeclaredConstructor().newInstance()

                // Replace by last displayed fragment
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()

            } catch (e: Exception) {
                e.printStackTrace()
                // Load First Page in case of error
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PatrolPage())
                    .commit()
            }
        }

        // Deactivate the flag after use, so that we load the last saved fragment only once after starting a meeting
        prefs.edit { putBoolean("should_restore_fragment", false) }
    }

    // Release resources on destroy
    override fun onDestroy() {
        super.onDestroy()
        // Do not need to restore last fragment saved after restarting/destroying the app
        getSharedPreferences("temi_state", MODE_PRIVATE).edit {
            putBoolean("should_restore_fragment", false)
                .remove("last_fragment")
        }
    }
}