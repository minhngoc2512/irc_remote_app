package com.minhngoc.irremote.atv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors

data class FoundTv(val name: String, val host: String, val port: Int)

/** Finds Android TV / Google TV devices advertising the remote service over mDNS. */
object AtvDiscovery {
    private const val SERVICE = "_androidtvremote2._tcp."
    private const val TAG = "AtvDiscovery"

    fun scan(context: Context): Flow<List<FoundTv>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = linkedMapOf<String, FoundTv>()
        val executor = Executors.newSingleThreadExecutor()

        fun resolved(info: NsdServiceInfo) {
            val host = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull()?.hostAddress else @Suppress("DEPRECATION") info.host?.hostAddress
            if (host == null) return
            synchronized(found) {
                found[info.serviceName] = FoundTv(info.serviceName, host, info.port)
                trySend(found.values.toList())
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(found) {
                    found.remove(info.serviceName)
                    trySend(found.values.toList())
                }
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= 34) {
                    nsd.registerServiceInfoCallback(info, executor, object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceUpdated(updated: NsdServiceInfo) {
                            resolved(updated)
                            runCatching { nsd.unregisterServiceInfoCallback(this) }
                        }
                        override fun onServiceLost() = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    })
                } else {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) = resolved(info)
                    })
                }
            }
        }
        trySend(emptyList())
        nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            executor.shutdown()
        }
    }
}
