package com.example.pointderassemblement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet.Layout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pointderassemblement.databinding.ActivityMainBinding
import com.example.pointderassemblement.ui.home.filePathSon1
import com.example.pointderassemblement.ui.home.filePathSon2
import com.example.pointderassemblement.ui.home.filePathSon3
import com.example.pointderassemblement.ui.home.filePathSon4
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding



    @SuppressLint("WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PackageManager.PERMISSION_GRANTED
        )
        val navView: BottomNavigationView = binding.navView



        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)


        val sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

// Récupérer les chemins de fichier des médias
        filePathSon1 = sharedPref.getString("mediaPlayerSon1Path", "") ?: ""
        filePathSon2 = sharedPref.getString("mediaPlayerSon2Path", "") ?: ""
        filePathSon3 = sharedPref.getString("mediaPlayerSon3Path", "") ?: ""
        filePathSon4 = sharedPref.getString("mediaPlayerSon4Path", "") ?: ""

    }


    override fun onDestroy() {
        super.onDestroy()

    }

    override fun onPause() {
        super.onPause()
        val sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

// Enregistrer les chemins de fichier des médias
        editor.putString("mediaPlayerSon1Path", filePathSon1)
        editor.putString("mediaPlayerSon2Path", filePathSon2)
        editor.putString("mediaPlayerSon3Path", filePathSon3)
        editor.putString("mediaPlayerSon4Path", filePathSon4)

        editor.apply()
    }
}