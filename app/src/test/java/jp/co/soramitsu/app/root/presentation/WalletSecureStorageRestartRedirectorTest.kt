package jp.co.soramitsu.app.root.presentation

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletSecureStorageRestartRedirectorTest {

    @Test
    fun `foreground latch redirects exactly once under repeated hostile callbacks`() {
        val registry = RestartListenerRegistry()
        val redirects = AtomicInteger()
        val redirector = redirector(
            registry = registry,
            canRedirect = { true },
            redirects = redirects
        )
        redirector.register()

        repeat(100) {
            registry.fire()
            redirector.onHostStarted()
        }

        assertEquals(1, redirects.get())
    }

    @Test
    fun `background latch waits until host starts then redirects once`() {
        val registry = RestartListenerRegistry()
        val redirects = AtomicInteger()
        var started = false
        val redirector = redirector(
            registry = registry,
            canRedirect = { started },
            redirects = redirects
        )
        redirector.register()

        registry.fire()
        assertEquals(0, redirects.get())

        started = true
        redirector.onHostStarted()
        redirector.onHostStarted()
        assertEquals(1, redirects.get())
    }

    @Test
    fun `already latched registration survives create-start boundary`() {
        val registry = RestartListenerRegistry(initiallyLatched = true)
        val redirects = AtomicInteger()
        var started = false
        val redirector = redirector(
            registry = registry,
            canRedirect = { started },
            redirects = redirects
        )

        redirector.register()
        assertEquals(0, redirects.get())

        started = true
        redirector.onHostStarted()
        assertEquals(1, redirects.get())
    }

    @Test
    fun `queued callback after destroy cannot redirect torn-down host`() {
        val registry = RestartListenerRegistry()
        val redirects = AtomicInteger()
        val mainQueue = ArrayDeque<() -> Unit>()
        val redirector = redirector(
            registry = registry,
            canRedirect = { true },
            redirects = redirects,
            postToMain = mainQueue::addLast
        )
        redirector.register()

        registry.fire()
        redirector.unregister()
        mainQueue.removeFirst().invoke()

        assertEquals(0, redirects.get())
        assertNull(registry.listener)
        assertEquals(1, registry.removeCalls.get())
    }

    @Test
    fun `destroyed host and recreated host do not duplicate redirect`() {
        val registry = RestartListenerRegistry()
        val oldRedirects = AtomicInteger()
        val oldHost = redirector(
            registry = registry,
            canRedirect = { false },
            redirects = oldRedirects
        )
        oldHost.register()
        registry.fire()
        oldHost.unregister()

        val newRedirects = AtomicInteger()
        val newHost = redirector(
            registry = registry,
            canRedirect = { true },
            redirects = newRedirects
        )
        newHost.register()
        newHost.onHostStarted()

        assertEquals(0, oldRedirects.get())
        assertEquals(1, newRedirects.get())
        assertEquals(1, registry.removeCalls.get())
    }

    private fun redirector(
        registry: RestartListenerRegistry,
        canRedirect: () -> Boolean,
        redirects: AtomicInteger,
        postToMain: (() -> Unit) -> Unit = { it() }
    ): WalletSecureStorageRestartRedirector {
        return WalletSecureStorageRestartRedirector(
            addRestartRequiredListener = registry::add,
            postToMain = postToMain,
            canRedirectNow = canRedirect,
            redirectToStartup = { redirects.incrementAndGet() }
        )
    }
}

private class RestartListenerRegistry(
    initiallyLatched: Boolean = false
) {
    private var latched = initiallyLatched
    var listener: (() -> Unit)? = null
        private set
    val removeCalls = AtomicInteger()

    fun add(candidate: () -> Unit): () -> Unit {
        listener = candidate
        if (latched) candidate()
        return {
            if (listener === candidate) listener = null
            removeCalls.incrementAndGet()
        }
    }

    fun fire() {
        latched = true
        listener?.invoke()
    }
}
