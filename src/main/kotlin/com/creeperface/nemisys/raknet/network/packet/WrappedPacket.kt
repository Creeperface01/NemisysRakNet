package com.creeperface.nemisys.raknet.network.packet

import com.creeperface.nemisys.raknet.network.BedrockSession
import com.nukkitx.network.raknet.CustomRakNetPacket
import io.netty.buffer.ByteBuf
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket
import java.util.*

/**
 * @author CreeperFace
 */
class WrappedPacket : CustomRakNetPacket<BedrockSession> {

    val packets = ArrayList<DataPacket>()
    var encrypted: Boolean = false
    var batched: ByteBuf? = null
    var payload: ByteBuf? = null

    override fun encode(buffer: ByteBuf) {
        buffer.writeBytes(payload)
        payload!!.release()
    }

    override fun decode(buffer: ByteBuf) {
        payload = buffer.readBytes(buffer.readableBytes())
    }

    @Throws(Exception::class)
    override fun handle(session: BedrockSession) {
        session.onWrappedPacket(this)
    }
}