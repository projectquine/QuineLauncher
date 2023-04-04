package com.example.quinelauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.quinelauncher.databinding.ActivityMainBinding
import android.content.Context
import android.net.wifi.WifiManager


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appList = ArrayList<App>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the title with the IP address
        val ipAddress = getIpAddress()
        title = "QuineOS ($ipAddress)"

        loadApps()

        binding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.adapter = AppAdapter(this, appList)
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
