package de.dude.db.database

import java.nio.MappedByteBuffer


private const val PAGE_SIZE = 4 * 1024 // 4 KB

class PagedMMF(path: String) : BufferedMMF(path) {

    init {
        val spaceAddr = (PAGE_SIZE - 1).toLong() * Long.SIZE_BYTES
        val freeAddr = atBuffer(spaceAddr, MappedByteBuffer::getLong)

        if (freeAddr == 0L) { // => empty DB
            atBuffer(spaceAddr) { putLong(it, PAGE_SIZE.toLong() * Long.SIZE_BYTES) }
        }
    }

    private fun occupy(size: Int): Long {
        if (size < 1 || size > PAGE_SIZE) {
            throw IllegalArgumentException("Occupy size must be between 1 and $PAGE_SIZE bytes")
        }

        var freeAddr = occupyInternal(size)
        if (freeAddr == 0L) {
            // no perfect fitting free space available -> use largest but not completely free space
            for (searchSize in PAGE_SIZE - 1 downTo size) {
                // TODO how to handle leftovers (small free spaces)
                freeAddr = occupyInternal(searchSize)
            }
            if (freeAddr == 0L) {
                // TODO how to handle leftovers (small free spaces)
                // no free space < PAGE_SIZE available -> use new page
                return occupyInternal(PAGE_SIZE)
            }
        }
        return freeAddr
    }

    // consider: how to make this atomic / transactional and concurrent?
    // TODO rename variables and make draft which specifies the naming of different dbFile sectors
    private fun occupyInternal(size: Int): Long {
        // "- 1", because free spaces of size 1 byte are stored at addr:0 -> everything is shifted by 1
        val spaceAddr = (size - 1).toLong() * Long.SIZE_BYTES
        val freeAddr = atBuffer(spaceAddr, MappedByteBuffer::getLong)

        if (freeAddr < 0) {
            // if negative, the absolute value is the address of the only available free space with the specified size
            atBuffer(spaceAddr) { putLong(it, 0) }
            return kotlin.math.abs(freeAddr)
        }
        if (freeAddr > 0) {
            // multiple free spaces of this size available that are stored in a bucket to which freeAddr is pointing
            val freeAddrIndex = atBuffer(freeAddr, MappedByteBuffer::getShort)
            // "+ Short.SIZE_BYTES", because numFreeAddr:short variable is stored at first place
            val lastFreeAddrPointer = freeAddr + Short.SIZE_BYTES + (freeAddrIndex * Long.SIZE_BYTES)
            val lastFreeAddr = atBuffer(lastFreeAddrPointer, MappedByteBuffer::getLong)

            if (freeAddrIndex > 0) {
                // remove last entry of bucket
                atBuffer(freeAddr) { putShort(it, (freeAddrIndex - 1).toShort()) }
                free(lastFreeAddrPointer, Long.SIZE_BYTES)
            } else {
                // bucket is empty -> remove bucket and reference
                atBuffer(spaceAddr) { putLong(it, 0) }
                free(freeAddr, Short.SIZE_BYTES + Long.SIZE_BYTES)
            }
            return lastFreeAddr
        }
        return 0
    }

    private fun free(addr: Long, size: Int) {

    }

}