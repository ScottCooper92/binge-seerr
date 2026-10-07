package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedStoresTest {
    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun `a scope's store is the same one when asked for again, as after a configuration change`() {
        val stores = ScopedStores()
        val first = stores.storeFor("setup")
        assertSame(first, stores.storeFor("setup"))
        assertNotSame(first, stores.storeFor("connected"))
    }

    @Test
    fun `clearing a scope clears its view models and the next ask gets a fresh store`() {
        val stores = ScopedStores()
        val probe = Probe()
        val store = stores.storeFor("setup")
        store.put("probe", probe)
        stores.clear("setup")
        assertTrue(probe.cleared)
        assertNotSame(store, stores.storeFor("setup"))
    }

    @Test
    fun `clearing one scope leaves the other alone`() {
        val stores = ScopedStores()
        val probe = Probe()
        stores.storeFor("connected").put("probe", probe)
        stores.clear("setup")
        assertFalse(probe.cleared)
    }
}
