package com.minhngoc.irremote.webos

import android.content.Context

/** Pairing keys and MAC addresses of LG TVs, per host. */
class PrefsWebOsStore(context: Context) : WebOsStore {
    private val prefs = context.applicationContext.getSharedPreferences("webos", Context.MODE_PRIVATE)
    override fun clientKey(host: String): String? = prefs.getString("key_$host", null)
    override fun saveClientKey(host: String, key: String) = prefs.edit().putString("key_$host", key).apply()
    override fun mac(host: String): String? = prefs.getString("mac_$host", null)
    override fun saveMac(host: String, mac: String) = prefs.edit().putString("mac_$host", mac).apply()
}
