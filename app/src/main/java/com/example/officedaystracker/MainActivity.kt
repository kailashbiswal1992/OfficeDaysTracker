package com.example.officedaystracker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class MainActivity : ComponentActivity() {
    private lateinit var geofencingClient: GeofencingClient
    private val prefs by lazy { getSharedPreferences("office", MODE_PRIVATE) }
    private val officeLatKey = "officeLat"
    private val officeLonKey = "officeLon"
    private val radiusKey = "radius"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geofencingClient = LocationServices.getGeofencingClient(this)

        val ivAppIcon = findViewById<ImageView>(R.id.ivAppIcon)
        val etLat = findViewById<TextInputEditText>(R.id.etLat)
        val etLon = findViewById<TextInputEditText>(R.id.etLon)
        val etRadius = findViewById<TextInputEditText>(R.id.etRadius)
        val btnSet = findViewById<MaterialButton>(R.id.btnSet)
        val btnViewCalendar = findViewById<View>(R.id.btnViewCalendar)
        val btnProgress = findViewById<View>(R.id.btnProgress)
        val tvOfficeDays = findViewById<TextView>(R.id.tvOfficeDays)
        val tvRemaining = findViewById<TextView>(R.id.tvRemaining)
        val progressQuarter = findViewById<ProgressBar>(R.id.progressQuarter)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Restore saved values
        val savedLat = prefs.getFloat(officeLatKey, Float.NaN)
        val savedLon = prefs.getFloat(officeLonKey, Float.NaN)
        val savedRadius = prefs.getFloat(radiusKey, 250f)
        if (!savedLat.isNaN()) etLat.setText(savedLat.toString())
        if (!savedLon.isNaN()) etLon.setText(savedLon.toString())
        etRadius.setText(savedRadius.toInt().toString())

        // Quick actions
        btnViewCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        btnProgress.setOnClickListener {
            // for now show calendar as progress placeholder
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        btnSet.setOnClickListener { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }

            val la = etLat.text?.toString()?.trim()?.toDoubleOrNull()
            val lo = etLon.text?.toString()?.trim()?.toDoubleOrNull()
            val r = etRadius.text?.toString()?.trim()?.toFloatOrNull() ?: 250f

            var ok = true
            if (la == null) {
                etLat.error = "Enter a valid latitude"
                ok = false
            } else etLat.error = null
            if (lo == null) {
                etLon.error = "Enter a valid longitude"
                ok = false
            } else etLon.error = null

            if (!ok) {
                Snackbar.make(view, "Please fix the errors above", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putFloat(officeLatKey, la!!.toFloat())
                .putFloat(officeLonKey, lo!!.toFloat())
                .putFloat(radiusKey, r)
                .apply()

            Snackbar.make(view, "Starting geofence...", Snackbar.LENGTH_SHORT).show()
            requestPermissionsAndStart(la, lo, r)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // already on home
                    true
                }
                R.id.navigation_calendar -> {
                    startActivity(Intent(this, CalendarActivity::class.java))
                    true
                }
                R.id.navigation_progress -> {
                    // open calendar for now
                    startActivity(Intent(this, CalendarActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Update dummy progress values (real calculation uses attendance prefs)
        tvOfficeDays.text = "8 / 24 Days"
        tvRemaining.text = "16 Days remaining"
        progressQuarter.progress = 33
    }

    private fun requestPermissionsAndStart(lat: Double, lon: Double, radius: Float) {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 10)
            return
        }

        if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 11)
            return
        }

        startGeofence(lat, lon, radius)
    }

    private fun startGeofence(lat: Double, lon: Double, radius: Float) {
        val geofence = Geofence.Builder()
            .setRequestId("OFFICE")
            .setCircularRegion(lat, lon, radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(this, OfficeGeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 1, intent, flags)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(findViewById(android.R.id.content), "Location permission is required", Snackbar.LENGTH_LONG).show()
            return
        }

        geofencingClient.addGeofences(request, pi).addOnSuccessListener {
            Snackbar.make(findViewById(android.R.id.content), "Automatic office detection enabled.", Snackbar.LENGTH_LONG).show()
        }.addOnFailureListener { ex ->
            Snackbar.make(findViewById(android.R.id.content), "Could not enable geofencing: ${ex.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
