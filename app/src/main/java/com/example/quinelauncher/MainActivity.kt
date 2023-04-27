package com.example.quinelauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.quinelauncher.databinding.ActivityMainBinding
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.widget.ImageView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


private val IP_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(1)

private val SSH_SERVICE_NAME = "quineOS-ssh"
private val SSH_SERVICE_TYPE = "_ssh._tcp."
private val HTTP_SERVICE_NAME = "quineOS-http"
private val HTTP_SERVICE_TYPE = "_http._tcp."
private val SERVICE_PORT = 80

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appList = ArrayList<App>()

    private lateinit var nsdManager: NsdManager
    private lateinit var registrationListener: NsdManager.RegistrationListener
    private lateinit var httpRegistrationListener: NsdManager.RegistrationListener

    private val DOCKER_CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(5)
    private lateinit var dockerStatusDot: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dockerStatusDot = findViewById(R.id.dockerApiStatusDot)

        // Initialize the Toolbar and set it as the ActionBar
        setSupportActionBar(binding.toolbar)
        // Get the UID of the com.termux app
        val packageManager = packageManager
        val termuxUid: Int

        try {
            termuxUid = packageManager.getApplicationInfo("com.termux", 0).uid
            // Set the title with the IP address
            updateTitleWithIpAddress(termuxUid)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MainActivity", "Termux app not found", e)
            Toast.makeText(this, "Termux app not found. Please install Termux.", Toast.LENGTH_LONG).show()
            updateTitleWithIpAddress(0)
        }

        // Start x11 and quineCamera apps and background them
        startApps()
        // load the apps we want to show in launcher
        loadApps()
        // Start our mDNS service
        registerService()

        binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.adapter = AppAdapter(this, appList)
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdManager.unregisterService(registrationListener)
        nsdManager.unregisterService(httpRegistrationListener)
    }

    private fun startApps() {
        // Start the X11 app
        val x11PackageName = "com.termux.x11"
        val x11Intent = packageManager.getLaunchIntentForPackage(x11PackageName)
        if (x11Intent != null) {
            x11Intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(x11Intent)
            // Wait for the X11 app to start
            Thread.sleep(5000)
        } else {
            // Show an error message if the app is not installed
            Toast.makeText(this, "App not installed: $x11PackageName", Toast.LENGTH_LONG).show()
            return
        }

        // Start the camera app
        val cameraPackageName = "com.example.quinecamera"
        val cameraIntent = packageManager.getLaunchIntentForPackage(cameraPackageName)
        if (cameraIntent != null) {
            cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(cameraIntent)
        } else {
            // Show an error message if the app is not installed
            Toast.makeText(this, "App not installed: $cameraPackageName", Toast.LENGTH_LONG).show()
            return
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateIpRunnable = object : Runnable {
        override fun run() {
            updateTitleWithIpAddress()
            handler.postDelayed(this, IP_UPDATE_INTERVAL)
        }
    }

    private fun updateTitleWithIpAddress(termuxUid: Int? = null) {
        val ipAddress = getIpAddress()
        supportActionBar?.title = "QuineOS (u0_a${termuxUid ?: packageManager.getApplicationInfo("com.termux", 0).uid % 10000}@$ipAddress)"
    }

    private fun updateDockerStatusDot(color: Int) {
        runOnUiThread {
            dockerStatusDot.setColorFilter(color)
        }
    }

    private fun checkDockerApiConnection() {
        val url = URL("http://localhost:2375/_ping")

        Thread {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.readTimeout = 10000
                connection.connectTimeout = 10000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode

                if (responseCode == 200) {
                    updateDockerStatusDot(Color.GREEN)
                } else {
                    updateDockerStatusDot(Color.RED)
                }

                connection.disconnect()
            } catch (e: IOException) {
                Log.e("checkDockerApiConnection", "API request failed", e)
                updateDockerStatusDot(Color.RED)
            }
        }.start()
    }

    private val checkDockerApiRunnable = object : Runnable {
        override fun run() {
            checkDockerApiConnection()
            handler.postDelayed(this, DOCKER_CHECK_INTERVAL)
        }
    }

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateIpRunnable)
        handler.post(checkDockerApiRunnable)

        val launcherIntent = Intent(Intent.ACTION_MAIN)
        launcherIntent.addCategory(Intent.CATEGORY_HOME)
        launcherIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(launcherIntent)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateIpRunnable)
        handler.removeCallbacks(checkDockerApiRunnable)
    }

    private fun loadApps() {
        val packageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (app in apps) {
            if (isAllowed(app.activityInfo.packageName)) {
                appList.add(App(app.loadLabel(packageManager).toString(), app.activityInfo.packageName, app.loadIcon(packageManager)))
            }
        }
    }

    private fun isAllowed(packageName: String): Boolean {
        val allowedPackages = arrayOf(
            "com.termux",
            "com.termux.x11",
            "com.topjohnwu.magisk",
            "com.android.settings",
            "net.christianbeier.droidvnc_ng",
            "com.tailscale.ipn",
            "com.example.quinecamera"
        )
        return allowedPackages.contains(packageName)
    }
    fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    fun openTermuxX11Preferences() {
        val intent = Intent().apply {
            setClassName("com.termux.x11", "com.termux.x11.LoriePreferences")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun initializeNsdRegistrationListener() {
        registrationListener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                val registeredServiceName = NsdServiceInfo.serviceName
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Service registered: $registeredServiceName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Service registration failed: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Service unregistered: ${arg0.serviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Service unregistration failed: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun initializeHttpNsdRegistrationListener() {
        httpRegistrationListener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                val registeredServiceName = NsdServiceInfo.serviceName
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "HTTP service registered: $registeredServiceName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "HTTP service registration failed: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "HTTP service unregistered: ${arg0.serviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "HTTP service unregistration failed: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun registerService() {
        val sshServiceInfo = NsdServiceInfo().apply {
            serviceName = SSH_SERVICE_NAME
            serviceType = SSH_SERVICE_TYPE
            port = SERVICE_PORT
        }

        val httpServiceInfo = NsdServiceInfo().apply {
            serviceName = HTTP_SERVICE_NAME
            serviceType = HTTP_SERVICE_TYPE
            port = SERVICE_PORT
        }

        nsdManager = (getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            initializeNsdRegistrationListener()
            initializeHttpNsdRegistrationListener()
            registerService(sshServiceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            registerService(httpServiceInfo, NsdManager.PROTOCOL_DNS_SD, httpRegistrationListener)
        }
    }

}
