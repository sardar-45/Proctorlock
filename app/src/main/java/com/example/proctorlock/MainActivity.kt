package com.example.proctorlock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.proctorlock.data.User
import com.example.proctorlock.firebase.FirebaseHelper
import com.example.proctorlock.screens.AdminScreen
import com.example.proctorlock.screens.AuthScreen
import com.example.proctorlock.screens.StudentScreen

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            var currentUser by remember { mutableStateOf<User?>(null) }

            when {
                currentUser == null -> {
                    AuthScreen(onLoggedIn = { currentUser = it })
                }
                currentUser!!.role == "admin" -> {
                    AdminScreen(
                        user = currentUser!!,
                        onLogout = { currentUser = null }
                    )
                }
                else -> {
                    StudentScreen(
                        user = currentUser!!,
                        onLogout = { currentUser = null }
                    )
                }
            }
        }
    }
}