package de.dude.db.util

import sun.misc.Unsafe

val UNSAFE by lazy { obtainUnsafe() }

private fun obtainUnsafe(): Unsafe {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    return field[null] as Unsafe
}