package com.temi.temi_robot.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController

class MapPage : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_map_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        RobotController.hideTopBar()

        val webView = view.findViewById<WebView>(R.id.webViewMap)
        val btnCloseMap = view.findViewById<Button>(R.id.btnCloseMap)

        // Configuration indispensable du WebView pour une carte interactive
        webView.webViewClient = WebViewClient() // Force l'ouverture DANS l'app, pas dans Chrome
        val settings: WebSettings = webView.settings

        settings.javaScriptEnabled = true // OBLIGATOIRE pour la map interactive NYP
        settings.domStorageEnabled = true // Permet de stocker les données de la map localement
        settings.builtInZoomControls = true // Active le zoom avec deux doigts
        settings.displayZoomControls = false // Cache les boutons de zoom laids (+/-) d'Android

        // Chargement de l'URL officielle de la carte du campus NYP
        webView.loadUrl("https://www.nyp.edu.sg/visit/campus-map")

        // Action du bouton Fermer : retour à la page d'accueil principale
        btnCloseMap.setOnClickListener {
            goBackToMainPage()
        }
    }

    private fun goBackToMainPage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainPage())
            .commit()
    }
}