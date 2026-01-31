package com.example.blocker

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Skeleton VpnService. Implementing a full packet-level blocker requires
 * parsing packets and handling routing which is non-trivial. This class
 * creates a VPN interface and keeps a foreground service running. Extend
 * this with packet I/O code or integrate an existing VPN/proxy library
 * to block domains.
 */
class BlockerVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.setSession("BlockerVpn")
        vpnInterface = builder.establish()
        return Service.START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
