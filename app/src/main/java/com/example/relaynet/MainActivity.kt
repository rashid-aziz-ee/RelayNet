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

    private var meshService: com.example.relaynet.mesh.MeshService? = null
    private var isBound = false
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
        RelayViewModelFactory(messageDao, myDeviceId, myName, this)
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as com.example.relaynet.mesh.MeshService.MeshBinder
            meshService = binder.getService()
            isBound = true

            // Start mesh engine through service
            meshService?.startMeshEngine { incomingEnvelope ->
                viewModel.onMessageReceived(incomingEnvelope)
            }

            // Bind ViewModel hooks to Service MeshManager
            meshService?.meshManager?.let { meshManager ->
                viewModel.sendLambda = { text ->
                    val recipientId = viewModel.selectedRecipientId.value
                    val recipientName = viewModel.selectedRecipientName.value
                    meshManager.broadcastNewMessage(text, myName, recipientId, recipientName)
                }

                meshManager.onPeersChangedCallback = { peers ->
                    viewModel.onPeersChanged(peers)
                }

                // Set initial peers
                viewModel.onPeersChanged(meshManager.connectedPeers)
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Compose content set karna
        setContent {
            RelayNetTheme {
                ChatScreen(viewModel = viewModel)
            }
        }

        // Permissions check karke mesh network start karna
        if (checkPermissions()) {
            startAndBindMeshService()
            Toast.makeText(this, "Mesh Network Started As: $myName", Toast.LENGTH_SHORT).show()
        } else {
            requestRequiredPermissions()
        }
    }

    // NEW: restart discovery/advertising whenever the app comes back to the foreground.
    // Nearby Connections' session can go stale after the app is backgrounded (screen lock,
    // switching apps, etc.), which previously required a full force-stop + relaunch to
    // recover. Re-calling startMesh() here re-establishes advertising/discovery cleanly
    // without needing that manual workaround.
    override fun onResume() {
        super.onResume()
        if (isBound) {
            meshService?.meshManager?.restartMesh()
        }
    }

    private fun startAndBindMeshService() {
        val serviceIntent = android.content.Intent(this, com.example.relaynet.mesh.MeshService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
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
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            // Legacy/Old Android permissions
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                startAndBindMeshService()
                Toast.makeText(this, "Permissions Granted. Mesh Started!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Some permissions were denied. Mesh needs Bluetooth + Nearby Devices + Location to work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}