package com.temi.temi_robot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class PasswordPage : Fragment(){
    private val correctPassword = "nyp123"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_password, container, false)

        val editTextPassword = view.findViewById<EditText>(R.id.editTextPassword)
        val checkboxShowPassword = view.findViewById<CheckBox>(R.id.checkboxShowPassword)
        val buttonGo = view.findViewById<Button>(R.id.buttonGo)

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
                // Change view to settings page
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SettingsPage())
                    .addToBackStack(null)
                    .commit()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Access Denied")
                    .setMessage("Incorrect password. Please try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        return view
    }
}