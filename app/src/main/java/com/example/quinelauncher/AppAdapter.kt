package com.example.quinelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quinelauncher.databinding.AppItemBinding

class AppAdapter(private val context: Context, private val appList: List<App>) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(val binding: AppItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = AppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appList[position]
        holder.binding.icon.setImageDrawable(app.icon)
        holder.binding.appName.text = app.name

        holder.binding.root.setOnClickListener {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            context.startActivity(intent)
        }

        holder.binding.root.setOnLongClickListener {
            if (context is MainActivity) {
                if (app.packageName == "com.termux.x11") {
                    context.openTermuxX11Preferences()
                } else {
                    context.openAppSettings(app.packageName)
                }
            }
            true
        }
    }

    override fun getItemCount() = appList.size
}
