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
import android.view.View
import android.widget.TextView
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.util.Log
import android.widget.ImageView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


private val IP_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(1)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appList = ArrayList<App>()

    private val DOCKER_CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(5)
    private lateinit var dockerStatusDot: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tvMagiskNotFound = findViewById<TextView>(R.id.tv_magisk_not_found)

        if (!isMagiskInstalled()) {
            tvMagiskNotFound.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            loadApps()
            binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
            binding.recyclerView.adapter = AppAdapter(this, appList)
            tvMagiskNotFound.visibility = View.GONE
        }

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

        binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.adapter = AppAdapter(this, appList)
    }

    override fun onDestroy() {
        super.onDestroy()
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

    private fun isMagiskInstalled(): Boolean {
        return try {
            packageManager.getApplicationInfo("com.topjohnwu.magisk", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
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

}
