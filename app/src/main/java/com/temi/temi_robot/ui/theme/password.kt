//class PAGE PASSWORD for config

package com.temi.checkbox
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.temi.temi_robot.R

class Password : AppCompatActivity() {
    private val correctPassword = "nyp123"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.password)

        val editTextPassword = findViewById<EditText>(R.id.editTextPassword)
        val checkboxShowPassword = findViewById<CheckBox>(R.id.checkboxShowPassword)
        val buttonGo = findViewById<Button>(R.id.buttonGo)

        checkboxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            editTextPassword.setSelection(editTextPassword.text.length)
        }

        buttonGo.setOnClickListener {
            val inputPassword = editTextPassword.text.toString()
            if (inputPassword == correctPassword) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Access Denied")
                    .setMessage("Incorrect password. Please try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}


