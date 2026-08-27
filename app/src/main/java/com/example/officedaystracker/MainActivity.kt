package com.example.officedaystracker

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.GeofencingRequest

class MainActivity : ComponentActivity() {
    private lateinit var geofencingClient: GeofencingClient
    private val prefs by lazy { getSharedPreferences("office", MODE_PRIVATE) }
    private val officeLatKey="officeLat"; private val officeLonKey="officeLon"; private val radiusKey="radius"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geofencingClient=LocationServices.getGeofencingClient(this)

        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(32,32,32,32)
        }
        val title=TextView(this).apply { text="Office Days Tracker"; textSize=26f }
        val info=TextView(this).apply {
            text="Set your office location and radius. The app can automatically record a day when you enter the office area."
            textSize=16f
        }
        val lat=EditText(this).apply {
    hint="Office latitude"
    setText("13.0426")
}

val lon=EditText(this).apply {
    hint="Office longitude"
    setText("77.6200")
}

val radius=EditText(this).apply {
    hint="Radius in metres (e.g. 250)"
    setText("250")
}
        val set=Button(this).apply { text="Enable automatic office detection" }
        val manual=Button(this).apply { text="Open attendance calendar" }
        val status=TextView(this).apply { textSize=15f }

        layout.addView(title); layout.addView(info); layout.addView(lat); layout.addView(lon); layout.addView(radius); layout.addView(set); layout.addView(manual); layout.addView(status)
        setContentView(layout)

        manual.setOnClickListener {
            Toast.makeText(this,"Calendar module is ready to be added; geofence settings are saved on this phone.",Toast.LENGTH_LONG).show()
        }

        set.setOnClickListener {
            val la=lat.text.toString().toDoubleOrNull()
            val lo=lon.text.toString().toDoubleOrNull()
            val r=radius.text.toString().toFloatOrNull() ?: 250f
            if(la==null || lo==null) { status.text="Please enter valid latitude and longitude."; return@setOnClickListener }
            prefs.edit().putFloat(officeLatKey,la.toFloat()).putFloat(officeLonKey,lo.toFloat()).putFloat(radiusKey,r).apply()
            requestPermissionsAndStart(la,lo,r)
        }
    }

    private fun requestPermissionsAndStart(lat:Double, lon:Double, radius:Float) {
        val permissions=mutableListOf<String>()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if(permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this,permissions.toTypedArray(),10)
        } else if(android.os.Build.VERSION.SDK_INT>=29 && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),11)
        } else startGeofence(lat,lon,radius)
    }

    private fun startGeofence(lat:Double,lon:Double,radius:Float) {
        val geofence=Geofence.Builder()
            .setRequestId("OFFICE")
            .setCircularRegion(lat,lon,radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request=GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofence(geofence).build()
        val pi=PendingIntent.getBroadcast(this,1,Intent(this,OfficeGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
        geofencingClient.addGeofences(request,pi).addOnSuccessListener {
            Toast.makeText(this,"Automatic office detection enabled.",Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
            Toast.makeText(this,"Could not enable geofencing: ${it.message}",Toast.LENGTH_LONG).show()
        }
    }
}
