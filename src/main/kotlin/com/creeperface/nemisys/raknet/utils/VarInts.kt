package com.creeperface.nemisys.raknet.utils

import io.netty.buffer.ByteBuf
import kotlin.experimental.and

/**
 * @author CreeperFace
 */
object VarInts {
    fun writeInt(buffer: ByteBuf, integer: Int) {
        encodeUnsigned(buffer, (integer shl 1 xor (integer shr 31)).toLong())
    }

    fun readInt(buffer: ByteBuf): Int {
        val n = decodeUnsigned(buffer).toInt()
        return n.ushr(1) xor -(n and 1)
    }

    fun writeUnsignedInt(buffer: ByteBuf, integer: Long) {
        encodeUnsigned(buffer, integer)
    }

    fun readUnsignedInt(buffer: ByteBuf): Int {
        return decodeUnsigned(buffer).toInt()
    }

    fun writeLong(buffer: ByteBuf, longInteger: Long) {
        encodeUnsigned(buffer, longInteger shl 1 xor (longInteger shr 63))
    }

    fun readLong(buffer: ByteBuf): Long {
        val n = decodeUnsigned(buffer)
        return n.ushr(1) xor -(n and 1)
    }

    fun writeUnsignedLong(buffer: ByteBuf, longInteger: Long) {
        encodeUnsigned(buffer, longInteger)
    }

    fun readUnsignedLong(buffer: ByteBuf): Long {
        return decodeUnsigned(buffer)
    }

    private fun decodeUnsigned(buffer: ByteBuf): Long {
        var result: Long = 0
        var shift = 0
        while (shift < 64) {
            val b = buffer.readByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80.toByte()).toInt() == 0) {
                return result
            }
            shift += 7
        }
        throw ArithmeticException("Varint was too large")
    }

    private fun encodeUnsigned(buffer: ByteBuf, value: Long) {
        var value = value

        while (true) {
            if (value and 0x7FL.inv() == 0L) {
                buffer.writeByte(value.toInt())
                return
            } else {
                buffer.writeByte((value.toInt() and 0x7F or 0x80).toByte().toInt())
                value = value ushr 7
            }
        }
    }
}