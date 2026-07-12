package com.example.relaynet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.relaynet.data.RelayNetDatabase
import com.example.relaynet.ui.ChatScreen
import com.example.relaynet.ui.RelayNetTheme
import com.example.relaynet.ui.RelayViewModel
import com.example.relaynet.ui.RelayViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var meshManager: MeshNetworkManager
    private val PERMISSION_REQUEST_CODE = 101

    // Initialize Room Database and DAO
    private val database by lazy { RelayNetDatabase.getDatabase(this) }
    private val messageDao by lazy { database.messageDao() }

    // Lazy initialization of Device ID and Name
    private val myDeviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).take(6)
    }
    private val myName by lazy {
        "Device_$myDeviceId"
    }

    // View Model with Factory to pass DAO and device configurations
    private val viewModel: RelayViewModel by viewModels {
        RelayViewModelFactory(messageDao, myDeviceId, myName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mesh Engine ko initialize karna
        meshManager = MeshNetworkManager(this, myDeviceId) { incomingEnvelope ->
            // Jab koi message mesh network se receive hoga, toh database aur UI updates direct call honge
            viewModel.onMessageReceived(incomingEnvelope)
        }

        // Integration Points Hook:
        // 1. Hook Send: UI calls viewModel.onSendMessage -> calls meshManager to broadcast
        viewModel.sendLambda = { text ->
            meshManager.broadcastNewMessage(text, myName)
        }

        // 2. Hook Peer changes: callback inside MeshNetworkManager forwards list of peers to ViewModel
        meshManager.onPeersChangedCallback = { peers ->
            viewModel.onPeersChanged(peers)
        }

        // Compose content set karna
        setContent {
            RelayNetTheme {
                ChatScreen(viewModel = viewModel)
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
        if (requestCode == PERMISSION_REQUEST_CODE) { // Fixed typo: PERMISSION_REQUEST_CODES to PERMISSION_REQUEST_CODE
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                meshManager.startMesh()
                Toast.makeText(this, "Permissions Granted. Mesh Started!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions Denied! Mesh cannot work.", Toast.LENGTH_LONG).show()
            }
        }
    }
}