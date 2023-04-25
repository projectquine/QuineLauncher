package com.example.quinelauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.quinelauncher.databinding.ActivityMainBinding
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.concurrent.TimeUnit
import android.widget.Toast


private val IP_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(1)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appList = ArrayList<App>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the UID of the com.termux app
        val packageManager = packageManager
        val termuxUid = packageManager.getApplicationInfo("com.termux", 0).uid

        // Set the title with the IP address
        updateTitleWithIpAddress(termuxUid)
        startApps()
        loadApps()

        binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.adapter = AppAdapter(this, appList)


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
        title = "QuineOS (u0_a${termuxUid ?: packageManager.getApplicationInfo("com.termux", 0).uid % 10000}@$ipAddress)"
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

        val launcherIntent = Intent(Intent.ACTION_MAIN)
        launcherIntent.addCategory(Intent.CATEGORY_HOME)
        launcherIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(launcherIntent)
    }


    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateIpRunnable)
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

}
