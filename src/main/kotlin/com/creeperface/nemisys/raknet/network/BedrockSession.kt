package com.creeperface.nemisys.raknet.network

import com.creeperface.nemisys.raknet.network.packet.WrappedPacket
import com.creeperface.nemisys.raknet.utils.VarInts
import com.google.common.base.Preconditions
import com.nukkitx.network.NetworkSession
import com.nukkitx.network.SessionConnection
import com.nukkitx.network.raknet.RakNetPacket
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import org.itxtech.nemisys.Player
import org.itxtech.nemisys.Server
import org.itxtech.nemisys.network.SourceInterface
import org.itxtech.nemisys.network.protocol.mcpe.BatchPacket
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket
import org.itxtech.nemisys.network.protocol.mcpe.DisconnectPacket
import org.itxtech.nemisys.utils.CompressionUtil
import org.itxtech.nemisys.utils.MainLogger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.DataFormatException


private val LOOPBACK_BEDROCK = InetSocketAddress(InetAddress.getLoopbackAddress(), 19132)
private val TIMEOUT_MS = 30000

/**
 * @author CreeperFace
 */
class BedrockSession(val server: Server, private val connection: SessionConnection<RakNetPacket>) : NetworkSession<RakNetPacket>, SourceInterface {

    private val packetQueue = ConcurrentLinkedQueue<DataPacket>()
    var player: Player
        private set

    private val lastKnownUpdate = AtomicLong()

    init {
        val address = connection.remoteAddress.get()
        player = Player(this, 0, address.hostName, address.port)

        server.addPlayer(player.rawHashCode().toString(), player)
    }

    private fun checkForClosed() = Preconditions.checkState(!connection.isClosed, "Connection has been closed!")

    private fun isTimedOut() = System.currentTimeMillis() - lastKnownUpdate.get() >= TIMEOUT_MS

    fun isClosed() = connection.isClosed

    override fun getConnection() = connection

    override fun disconnect() = disconnect("disconnect.disconnected")

    @JvmOverloads
    fun disconnect(reason: String, hideReason: Boolean = false) {
        checkForClosed()

        val pk = DisconnectPacket()
        pk.message = reason
        pk.hideDisconnectionScreen = hideReason

        this.player.sendDataPacket(pk)

        close()
    }

    override fun touch() {
        checkForClosed()
        lastKnownUpdate.set(System.currentTimeMillis())
    }

    override fun onTick() {
        if (isClosed()) {
            return
        }

        if (isTimedOut()) {
            disconnect("disconnect.timeout")
            return
        }

        connection.onTick()
    }

    private fun processQueued() {
        val packet = WrappedPacket()

        while (packetQueue.isNotEmpty()) {
            val pk = packetQueue.poll()

            if (pk is BatchPacket) {
                sendPacket(pk)

                try {
                    Thread.sleep(1)
                } catch (e: InterruptedException) {
                    //ignore
                }

                continue
            }

            packet.packets.add(pk)
        }

        if (packet.packets.isNotEmpty()) {
            sendPacket(packet)
        }
    }

    @Throws(Exception::class)
    fun onWrappedPacket(packet: WrappedPacket) {
        Preconditions.checkNotNull<Any>(packet, "packet")

        val wrappedData = packet.payload!!

        try {
            for (pk in decompressPackets(wrappedData)) {
                player.addOutgoingPacket(pk)
            }
        } finally {
            wrappedData.release()
        }
    }

    fun compressPackets(packets: Collection<DataPacket>): ByteBuf {
        val source = PooledByteBufAllocator.DEFAULT.directBuffer()

        try {
            for (packet in packets) {
                var packetBuf: ByteBuf? = null

                try {
                    if (!packet.isEncoded) {
                        packet.encode()
                    }

                    packetBuf = Unpooled.wrappedBuffer(packet.buffer)

                    VarInts.writeUnsignedInt(source, packetBuf.readableBytes().toLong())
                    source.writeBytes(packetBuf)
                } catch (e: Exception) {
                    MainLogger.getLogger().logException(e)
                } finally {
                    packetBuf?.release()
                }
            }
            return CompressionUtil.zlibDeflate(source)
        } catch (e: DataFormatException) {
            throw RuntimeException("Unable to deflate buffer data", e)
        } finally {
            source.release()
        }
    }

    fun decompressPackets(compressed: ByteBuf): List<DataPacket> {
        val packets = ArrayList<DataPacket>()
        var decompressed: ByteBuf? = null
        try {
            try {
                decompressed = CompressionUtil.zlibInflate(compressed)
            } catch (e: Exception) {
                throw RuntimeException("Unable to decompress packet", e)
            }

            while (decompressed!!.isReadable) {
                val length = VarInts.readUnsignedInt(decompressed)
                val data = decompressed.readSlice(length)

                if (!data.isReadable) {
                    throw DataFormatException("Contained packet is empty.")
                }

                compressed.readUnsignedInt()
                val pkg = server.network.getPacket(data.getByte(0))
                if (pkg != null) {
                    val buffer = ByteArray(data.readableBytes())
                    data.readBytes(buffer)

                    pkg.buffer = buffer
                    pkg.decode()

                    packets.add(pkg)
                } /*else {
                    data.readerIndex(0)
                    val unknown = UnknownPacket()
                    unknown.decode(data)
                    packets.add(unknown)
                }*/
            }
        } catch (e: DataFormatException) {
            throw RuntimeException("Unable to inflate buffer data", e)
        } finally {
            decompressed?.release()
        }
        return packets
    }

    override fun emergencyShutdown() {
        disconnect()
    }

    override fun shutdown() {
        disconnect()
    }

    override fun setName(p0: String?) {

    }

    override fun getNetworkLatency(p0: Player?) = 0

    override fun putPacket(player: Player, pk: DataPacket) = putPacket(player, pk, false)

    override fun putPacket(player: Player, pk: DataPacket, needAck: Boolean) = putPacket(player, pk, needAck, false)

    override fun putPacket(player: Player, pk: DataPacket, needAck: Boolean, immediate: Boolean): Int? {
        checkForClosed()

        if (immediate)
            sendPacket(pk)
        else
            packetQueue.add(pk)

        return null
    }

    private fun sendPacket(pk: DataPacket) {
        val packet = WrappedPacket()

        if (pk !is BatchPacket) {
            packet.packets.add(pk)
        } else {
            packet.batched = Unpooled.wrappedBuffer(pk.payload)
        }

        sendPacket(packet)
    }

    private fun sendPacket(pk: WrappedPacket) {
        val compressed = if (pk.batched != null) pk.batched!! else compressPackets(pk.packets)

        pk.payload = compressed
        connection.sendPacket(pk)
    }

    override fun process() = true

    override fun close(player: Player?) {
        close(player, "generic reason")
    }

    override fun close(player: Player?, reason: String?) {
        disconnect(reason ?: "generic reason")
    }

    fun close() {
        connection.close()
        player.close()
    }
}