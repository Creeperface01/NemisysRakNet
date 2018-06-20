package com.creeperface.nemisys.raknet

import com.creeperface.nemisys.raknet.network.BedrockSession
import com.creeperface.nemisys.raknet.network.NemisysSessionManager
import com.creeperface.nemisys.raknet.network.packet.WrappedPacket
import com.nukkitx.network.raknet.RakNetEventListener
import com.nukkitx.network.raknet.RakNetServer
import org.itxtech.nemisys.network.RakNetInterface
import org.itxtech.nemisys.network.protocol.mcpe.ProtocolInfo
import org.itxtech.nemisys.plugin.PluginBase
import org.itxtech.nemisys.utils.MainLogger
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * @author CreeperFace
 */
class NemisysRaknet : PluginBase() {

    var rakNetServer: RakNetServer<BedrockSession>? = null
        private set

    override fun onLoad() {
        saveDefaultConfig()
        val cfg = config

        val ip = cfg.getString("server-ip")
        val port = cfg.getInt("server-port")

        val disableRaknet = cfg.getBoolean("disable-default-raknet")

        if (disableRaknet) {
            server.network.interfaces.forEach({ if (it is RakNetInterface) it.shutdown() })
        }

        val maxThreads = Runtime.getRuntime().availableProcessors()
        rakNetServer = RakNetServer.builder<BedrockSession>().address(ip, port)
                .listener(object : RakNetEventListener {
                    override fun onConnectionRequest(p0: InetSocketAddress?): RakNetEventListener.Action {
                        MainLogger.getLogger().info("connection request")
                        return RakNetEventListener.Action.CONTINUE
                    }

                    override fun onQuery(p0: InetSocketAddress?): RakNetEventListener.Advertisement {
                        MainLogger.getLogger().info("query request")

                        return RakNetEventListener.Advertisement(
                                "MCPE",
                                server.network.name,
                                ProtocolInfo.CURRENT_PROTOCOL,
                                ProtocolInfo.MINECRAFT_VERSION,
                                server.onlinePlayers.size,
                                server.maxPlayers,
                                "",
                                "minigames"
                        )
                    }
                })
                .packet({ WrappedPacket() }, 0xfe)
                .maximumThreads(maxThreads)
                .serverId(SecureRandom().nextLong())
                .sessionFactory { connection -> BedrockSession(server, connection) }
                .sessionManager(NemisysSessionManager())
                .build()
        rakNetServer!!.rakNetNetworkListener.bind()
    }

    override fun onDisable() {
        rakNetServer?.rakNetNetworkListener?.close()
    }
}