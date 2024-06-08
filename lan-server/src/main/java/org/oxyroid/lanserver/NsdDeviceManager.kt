package org.oxyroid.lanserver

import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.Flow

interface NsdDeviceManager {
    fun search(): Flow<LanService.ServerInfo>
    fun broadcast(
        name: String = SERVER_NAME,
        port: Int,
        metadata: Map<String, Any> = emptyMap()
    ): Flow<LanService.ServerInfo?>

    companion object {
        private const val NSD_FLAG = "F_LAN_SERVER"
        private val SERVER_NAME = "${NSD_FLAG.uppercase()}_BROADCAST"
        internal const val SERVICE_TYPE = "_$NSD_FLAG-server._tcp."
        internal const val META_DATA_PORT = "port"
        internal const val META_DATA_HOST = "host"
    }
}

fun NsdServiceInfo.toServerInfoOrNull(): LanService.ServerInfo? {
    return LanService.ServerInfo(
        host = getAttribute(NsdDeviceManager.META_DATA_HOST) ?: return null,
        port = getAttribute(NsdDeviceManager.META_DATA_PORT)?.toInt() ?: return null
    )
}

private fun NsdServiceInfo.getAttribute(key: String): String? = attributes[key]?.decodeToString()