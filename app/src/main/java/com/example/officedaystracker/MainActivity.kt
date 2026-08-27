package com.example.officedaystracker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.officedaystracker.BuildConfig
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var geofencingClient: GeofencingClient
    private val prefs by lazy { getSharedPreferences("office", MODE_PRIVATE) }
    private val attendancePrefs by lazy { getSharedPreferences("attendance", MODE_PRIVATE) }
    private val officeLatKey = "officeLat"
    private val officeLonKey = "officeLon"
    private val radiusKey = "radius"
    private val quarterTarget = 24 // target office days per quarter (as in mock)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geofencingClient = LocationServices.getGeofencingClient(this)

        val ivAppIcon = findViewById<ImageView>(R.id.ivAppIcon)
        val etLat = findViewById<TextInputEditText>(R.id.etLat)
        val etLon = findViewById<TextInputEditText>(R.id.etLon)
        val etRadius = findViewById<TextInputEditText>(R.id.etRadius)
        val btnSet = findViewById<MaterialButton>(R.id.btnSet)
        val cardCalendar = findViewById<MaterialCardView>(R.id.cardCalendar)
        val cardProgress = findViewById<MaterialCardView>(R.id.cardProgress)
        val tvOfficeDays = findViewById<TextView>(R.id.tvOfficeDays)
        val tvRemaining = findViewById<TextView>(R.id.tvRemaining)
        val progressQuarter = findViewById<ProgressBar>(R.id.progressQuarter)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Attempt to set a bundled hero drawable if present (resource name: hero)
        val heroId = resources.getIdentifier("hero", "drawable", packageName)
        if (heroId != 0) ivAppIcon.setImageResource(heroId)

        // Attempt to apply a bundled font (resource name: poppins_regular in /res/font)
        val fontId = resources.getIdentifier("poppins_regular", "font", packageName)
        if (fontId != 0) {
            val tf = ResourcesCompat.getFont(this, fontId)
            findViewById<TextView>(R.id.tvHeaderTitle).typeface = tf
            findViewById<TextView>(R.id.tvHeaderSubtitle).typeface = tf
        }

        // Restore saved values
        val savedLat = prefs.getFloat(officeLatKey, Float.NaN)
        val savedLon = prefs.getFloat(officeLonKey, Float.NaN)
        val savedRadius = prefs.getFloat(radiusKey, 250f)
        if (!savedLat.isNaN()) etLat.setText(savedLat.toString())
        if (!savedLon.isNaN()) etLon.setText(savedLon.toString())
        etRadius.setText(savedRadius.toInt().toString())

        // Quick actions - add small press animation
        val touchAnim: (View) -> Unit = { v ->
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
        }

        cardCalendar.setOnClickListener {
            touchAnim(it)
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        cardProgress.setOnClickListener {
            touchAnim(it)
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

            // update progress after saving
            updateQuarterProgressUI()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_calendar -> {
                    startActivity(Intent(this, CalendarActivity::class.java)); true
                }
                R.id.navigation_progress -> {
                    startActivity(Intent(this, CalendarActivity::class.java)); true
                }
                else -> false
            }
        }

        // Add a debug-only "Mark Today" button programmatically (appears only in debug builds)
        if (BuildConfig.DEBUG) {
            try {
                val cardOffice = findViewById<MaterialCardView>(R.id.cardOfficeLocation)
                val container = (cardOffice.getChildAt(0) as? ViewGroup)
                container?.let { parent ->
                    val markBtn = MaterialButton(this).apply {
                        text = "Mark Today (dev)"
                        isAllCaps = false
                        setOnClickListener { v ->
                            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            attendancePrefs.edit().putBoolean(todayKey, true).apply()
                            updateQuarterProgressUI()
                            Snackbar.make(v, "Marked $todayKey as office day", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    // small top margin
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = (8 * resources.displayMetrics.density).toInt()
                    parent.addView(markBtn, lp)
                }
            } catch (e: Exception) {
                // Ignore if layout isn't as expected
            }
        }

        // initial UI update
        updateQuarterProgressUI()
    }

    private fun updateQuarterProgressUI() {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val quarterStart = (month / 3) * 3

        var officeDays = 0

        for (m in quarterStart until quarterStart + 3) {
            val calendar = Calendar.getInstance()
            calendar.set(year, m, 1)
            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..maxDay) {
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
                if (attendancePrefs.getBoolean(key, false)) officeDays++
            }
        }

        val remaining = (quarterTarget - officeDays).coerceAtLeast(0)
        val progressPercent = if (quarterTarget > 0) (officeDays * 100) / quarterTarget else 0

        // update UI
        findViewById<TextView>(R.id.tvOfficeDays).text = "$officeDays / $quarterTarget Days"
        findViewById<TextView>(R.id.tvRemaining).text = "$remaining Days remaining"
        findViewById<ProgressBar>(R.id.progressQuarter).progress = progressPercent
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
            updateQuarterProgressUI()
        }.addOnFailureListener { ex ->
            Snackbar.make(findViewById(android.R.id.content), "Could not enable geofencing: ${ex.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
