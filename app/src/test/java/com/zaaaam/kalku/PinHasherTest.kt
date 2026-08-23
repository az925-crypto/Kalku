package com.zaaaam.kalku

import com.zaaaam.kalku.core.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test fun hashAndVerifyRoundtrip() {
        val stored = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("9999", stored))
        assertFalse(PinHasher.verify("", stored))
        assertFalse(PinHasher.verify("12345", stored))
    }

    @Test fun saltMakesHashesUnique() {
        val a = PinHasher.hash("secret")
        val b = PinHasher.hash("secret")
        assertNotEquals(a, b)
        assertTrue(PinHasher.verify("secret", a))
        assertTrue(PinHasher.verify("secret", b))
    }

    @Test fun garbageStoredValuesFailClosed() {
        assertFalse(PinHasher.verify("x", null))
        assertFalse(PinHasher.verify("x", ""))
        assertFalse(PinHasher.verify("x", "not-a-hash"))
        assertFalse(PinHasher.verify("x", "v9::bad::worse"))
        assertFalse(PinHasher.verify("x", "v1:abc:def:ghi"))
    }
}
