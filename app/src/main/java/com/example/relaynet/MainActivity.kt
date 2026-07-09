package com.example.relaynet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var meshManager: MeshNetworkManager
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Har phone ke liye ek unique name/ID generate karna settings se
        val myDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).take(6)
        val myName = "Device_$myDeviceId"

        // Mesh Engine ko initialize karna
        meshManager = MeshNetworkManager(this, myDeviceId) { incomingEnvelope ->
            // Jab koi message mesh network se receive hoga, toh yeh block chalay ga
            runOnUiThread {
                Toast.makeText(
                    this,
                    "${incomingEnvelope.senderName}: ${incomingEnvelope.payload}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Permissions check karke mesh network start karna
        if (checkPermissions()) {
            meshManager.startMesh()
            Toast.makeText(this, "Mesh Network Started As: $myName", Toast.LENGTH_SHORT).show()
        } else {
            requestRequiredPermissions()
        }
    }

    // --- Permissions Handling Logic ---
    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            // Legacy/Old Android permissions
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODES) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                meshManager.startMesh()
                Toast.makeText(this, "Permissions Granted. Mesh Started!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions Denied! Mesh cannot work.", java.lang.Long.MAX_VALUE.toInt()).show()
            }
        }
    }
}