package com.example.quinelauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.quinelauncher.databinding.ActivityMainBinding
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

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

        loadApps()

        binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.adapter = AppAdapter(this, appList)
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
            "net.christianbeier.droidvnc_ng"
        )
        return allowedPackages.contains(packageName)
    }

}
