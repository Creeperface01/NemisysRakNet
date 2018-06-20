package com.creeperface.nemisys.raknet.network

import com.flowpowered.math.GenericMath
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.nukkitx.network.SessionManager
import org.itxtech.nemisys.Player
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * @author CreeperFace
 */
class NemisysSessionManager : SessionManager<BedrockSession> {

    private val sessions = ConcurrentHashMap<InetSocketAddress, BedrockSession>()
    private val playerSessions = ConcurrentHashMap<UUID, Player>()
    private val sessionTicker = ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES,
            LinkedBlockingQueue(), ThreadFactoryBuilder().setNameFormat("Session Ticker - #%d").setDaemon(true).build())

    override fun add(address: InetSocketAddress, session: BedrockSession): Boolean {
        val added = sessions.putIfAbsent(address, session) == null
        if (added) {
            adjustPoolSize()
        }
        return added
    }

    override fun remove(session: BedrockSession): Boolean {
        val removed = sessions.values.remove(session)

        playerSessions.remove(session.player.uuid)

        if (removed) {
            adjustPoolSize()
        }
        return removed
    }

    override fun get(address: InetSocketAddress): BedrockSession? {
        return sessions[address]
    }

    override fun all(): Collection<BedrockSession> {
        return ImmutableList.copyOf(sessions.values)
    }

    override fun getCount(): Int {
        return sessions.size
    }

    fun playerSessionCount(): Int {
        return playerSessions.size
    }

    fun add(session: BedrockSession): Boolean {
        return playerSessions.putIfAbsent(session.player.uuid, session.player) == null
    }

    fun getPlayer(uuid: UUID): Player? {
        return playerSessions[uuid]
    }

    fun getPlayer(name: String): Player? {
        var name = name
        var found: Player? = null
        name = name.toLowerCase()
        var delta = Integer.MAX_VALUE
        for (player in allPlayers()) {
            if (player.name.toLowerCase().startsWith(name)) {
                val curDelta = player.name.length - name.length
                if (curDelta < delta) {
                    found = player
                    delta = curDelta
                }
                if (curDelta == 0) {
                    break
                }
            }
        }

        return found
    }

    fun allPlayers(): List<Player> {
        return ImmutableList.copyOf(playerSessions.values)
    }

    private fun adjustPoolSize() {
        val threads = GenericMath.clamp(sessions.size / SESSIONS_PER_THREAD, 1, Runtime.getRuntime().availableProcessors())
        if (sessionTicker.maximumPoolSize != threads) {
            sessionTicker.maximumPoolSize = threads
        }
    }

    override fun onTick() {
        for (session in sessions.values) {
            sessionTicker.execute { session.onTick() }
        }
    }

    companion object {
        private val SESSIONS_PER_THREAD = 50
    }
}