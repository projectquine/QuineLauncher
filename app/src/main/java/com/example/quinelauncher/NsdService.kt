package com.example.quinelauncher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

private const val NOTIFICATION_CHANNEL_ID = "QuineServiceChannel"

private val SSH_SERVICE_NAME = "quineOS-ssh"
private val SSH_SERVICE_TYPE = "_ssh._tcp."
private val HTTP_SERVICE_NAME = "quineOS-http"
private val HTTP_SERVICE_TYPE = "_http._tcp."
private val SERVICE_PORT = 80

class NsdService : Service() {

    private lateinit var nsdManager: NsdManager
    private lateinit var registrationListener: NsdManager.RegistrationListener
    private lateinit var httpRegistrationListener: NsdManager.RegistrationListener

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Quine Service")
            .setContentText("Quine Service is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        registerService()
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdManager.unregisterService(registrationListener)
        nsdManager.unregisterService(httpRegistrationListener)
    }

    private fun initializeNsdRegistrationListener() {
        registrationListener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                val registeredServiceName = NsdServiceInfo.serviceName
                Log.i("NsdService", "Quine SSH Service registered: $registeredServiceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.i("NsdService", "Quine SSH Service failed: $serviceInfo")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i("NsdService", "Quine SSH Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.i("NsdService", "Quine SSH Service failed to unregistered")
                }
            }
        }

    private fun initializeHttpNsdRegistrationListener() {
        httpRegistrationListener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                val registeredServiceName = NsdServiceInfo.serviceName
                Log.i("NsdService", "Quine HTTP Service registered: $registeredServiceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.i("NsdService", "Quine HTTP Service failed: $serviceInfo")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i("NsdService", "Quine HTTP Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.i("NsdService", "Quine HTTP Service failed to unregistered")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Quine Service Channel"
            val descriptionText = "Quine Service Channel for running in the foreground"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
