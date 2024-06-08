package org.oxyroid.lanserver

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.oxyroid.lanserver.NsdDeviceManager.Companion.SERVICE_TYPE

internal class NsdDeviceManagerImpl(
    private val nsdManager: NsdManager,
) : NsdDeviceManager {
    override fun search(): Flow<LanService.ServerInfo> = callbackFlow {

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
//                logger.log("start discovery failed, error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
//                logger.log("stop discovery failed, error code: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
//                logger.log("discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
//                logger.log("discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                        ) {
//                            logger.log("resolve service, error code: $errorCode.")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            serviceInfo.toServerInfoOrNull()?.let { trySend(it) }
//                            logger.log("service resolved: $serviceInfo")
                        }

                        override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
//                            logger.log("resolution stopped: $serviceInfo")
                        }
                    }.also {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(
                            serviceInfo,
                            it
                        )
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
//                    logger.log("service lost: $serviceInfo")
                }
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            nsdManager.stopServiceDiscovery(listener)
        }
    }
        .flowOn(Dispatchers.IO)

    override fun broadcast(
        name: String,
        port: Int,
        metadata: Map<String, Any>
    ): Flow<LanService.ServerInfo?> = callbackFlow {
//        logger.log("broadcast")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
//            setAttribute(META_DATA_PIN, pin.toString())
            metadata.forEach { setAttribute(it.key, it.value.toString()) }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {
                trySendBlocking(serviceInfo)
//                activatedBroadcasts.value += serviceInfo
//                logger.log("broadcast registered")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                trySendBlocking(null)
//                activatedBroadcasts.value -= serviceInfo
//                logger.log("broadcast un-registered")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                trySendBlocking(null)
//                activatedBroadcasts.value -= serviceInfo
//                logger.log("registration failed, error code: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                trySendBlocking(null)
//                activatedBroadcasts.value -= serviceInfo
//                logger.log("un-registration failed, error code: $errorCode")
            }

        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            nsdManager.unregisterService(listener)
            trySendBlocking(null)
//            activatedBroadcasts.value -= serviceInfo
        }
    }
        .map { it?.toServerInfoOrNull() }
        .flowOn(Dispatchers.IO)
}