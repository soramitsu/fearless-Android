package jp.co.soramitsu.common.validation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.resources.ResourceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

class ValidationExecutorLifetimeTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val executor = ValidationExecutor(mock<ResourceManager>())
    private val errors = mutableListOf<Throwable>()
    private val delivered = mutableListOf<Int>()
    private val progress = mutableListOf<Boolean>()
    private var current = true

    @Test fun `current successful validation preserves payload`() = runTest {
        execute(system { ValidationStatus.Valid() })
        assertEquals(listOf(7), delivered); assertTrue(errors.isEmpty())
    }

    @Test fun `current warning confirmation applies payload fix`() = runTest {
        execute(warning(), fix = { p, _ -> p + 1 })
        assertTrue(delivered.isEmpty()); executor.validationFailureEvent.value!!.peekContent().confirmWarning()
        assertEquals(listOf(8), delivered)
    }

    @Test fun `obsolete action never begins validation`() = runTest {
        current = false; var calls = 0
        execute(system { calls++; ValidationStatus.Valid() })
        assertEquals(0, calls); assertTrue(progress.isEmpty()); assertTrue(delivered.isEmpty())
    }

    @Test fun `progress reentrancy revokes validation before lookup`() = runTest {
        var calls = 0
        execute(system { calls++; ValidationStatus.Valid() }, onProgress = { current = false })
        assertEquals(0, calls); assertTrue(delivered.isEmpty())
    }

    @Test fun `late successful result cannot deliver after ownership loss`() = runTest {
        execute(system { current = false; ValidationStatus.Valid() })
        assertTrue(delivered.isEmpty()); assertTrue(errors.isEmpty())
    }

    @Test fun `late rejected result cannot present after ownership loss`() = runTest {
        execute(system { current = false; ValidationStatus.NotValid(DefaultFailureLevel.WARNING, "warning") })
        assertNull(executor.validationFailureEvent.value)
    }

    @Test fun `failure progress callback can revoke error display`() = runTest {
        execute(system { throw IOException("local") }, onProgress = { if (!it) current = false })
        assertTrue(errors.isEmpty()); assertTrue(delivered.isEmpty())
    }

    @Test fun `failure transformation cannot present after replacing owner`() = runTest {
        execute(warning(), transform = { current = false; "title" to "message" })
        assertNull(executor.validationFailureEvent.value)
    }

    @Test fun `retained warning callback cannot revive obsolete action`() = runTest {
        var fixes = 0
        execute(warning(), fix = { p, _ -> fixes++; p })
        current = false; executor.validationFailureEvent.value!!.peekContent().confirmWarning()
        assertEquals(0, fixes); assertTrue(delivered.isEmpty()); assertEquals(listOf(true, false), progress)
    }

    @Test fun `warning confirmation progress cannot revive replaced owner`() = runTest {
        var confirming = false; var fixes = 0
        execute(warning(), onProgress = { if (confirming) current = false }, fix = { p, _ -> fixes++; p })
        confirming = true; executor.validationFailureEvent.value!!.peekContent().confirmWarning()
        assertEquals(0, fixes); assertTrue(delivered.isEmpty())
    }

    @Test fun `warning fix callback cannot deliver after replacing owner`() = runTest {
        execute(warning(), fix = { p, _ -> current = false; p })
        executor.validationFailureEvent.value!!.peekContent().confirmWarning()
        assertTrue(delivered.isEmpty())
    }

    @Test fun `lookup cancellation is rethrown without ordinary error output`() = runTest {
        var cancellation: CancellationException? = null
        try { execute(system { throw CancellationException("cancel local validation") }) }
        catch (error: CancellationException) { cancellation = error }
        assertNotNull(cancellation); assertTrue(errors.isEmpty()); assertTrue(delivered.isEmpty())
        assertNull(executor.validationFailureEvent.value)
    }

    @Test fun `current cancellation clears progress before propagating`() = runTest {
        var cancellation: CancellationException? = null
        try { execute(system { throw CancellationException("cancel current lookup") }) }
        catch (error: CancellationException) { cancellation = error }
        assertNotNull(cancellation); assertEquals(listOf(true, false), progress)
        assertTrue(errors.isEmpty()); assertTrue(delivered.isEmpty())
    }

    @Test fun `obsolete cancellation cannot publish progress cleanup`() = runTest {
        var cancellation: CancellationException? = null
        try { execute(system { current = false; throw CancellationException("cancel obsolete lookup") }) }
        catch (error: CancellationException) { cancellation = error }
        assertNotNull(cancellation); assertEquals(listOf(true), progress)
        assertTrue(errors.isEmpty()); assertTrue(delivered.isEmpty())
    }

    private fun system(body: suspend () -> ValidationStatus<String>) = ValidationSystem(object : Validation<Int, String> {
        override suspend fun validate(value: Int) = body()
    })
    private fun warning() = system { ValidationStatus.NotValid(DefaultFailureLevel.WARNING, "warning") }
    private suspend fun execute(
        system: ValidationSystem<Int, String>,
        onProgress: (Boolean) -> Unit = {},
        transform: (String) -> Pair<String, String> = { "title" to it },
        fix: (Int, String) -> Int = { p, _ -> p }
    ) {
        executor.requireValid(system, 7, errors::add, transform,
            progressConsumer = { progress += it; onProgress(it) }, autoFixPayload = fix,
            isCurrent = { current }, block = delivered::add)
    }
}
