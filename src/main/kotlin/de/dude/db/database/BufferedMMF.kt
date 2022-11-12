package de.dude.db.database

import de.dude.db.util.UNSAFE
import java.io.Closeable
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

private const val BUFFER_SIZE = 64L * 1024 // TODO * 1024 // 64 MB

open class BufferedMMF(path: String) : Closeable {

    private val channel = FileChannel.open(Path.of(path), CREATE, WRITE, READ)
    private val buffers = mutableListOf<MappedByteBuffer>()

    init {
        val fileSize = channel.size()
        allocBuffers(if (fileSize > 0) fileSize - 1 else 0)
    }

    protected fun <T> atBuffer(address: Long, action: MappedByteBuffer.(relAddr: Int) -> T): T {
        allocBuffers(address)
        val bufferIndex = (address / BUFFER_SIZE).toInt()
        val relAddr = (address % BUFFER_SIZE).toInt()

        return buffers[bufferIndex].action(relAddr)
    }

    private fun allocBuffers(untilAddr: Long) {
        for (address in buffers.size * BUFFER_SIZE..untilAddr step BUFFER_SIZE) {
            buffers.add(channel.map(FileChannel.MapMode.READ_WRITE, address, BUFFER_SIZE))
        }
    }

    private fun removeLastBuffer() = UNSAFE.invokeCleaner(buffers.removeLast().apply { force() })

    override fun close() {
        repeat(buffers.size) { removeLastBuffer() }
        channel.close()
    }
}