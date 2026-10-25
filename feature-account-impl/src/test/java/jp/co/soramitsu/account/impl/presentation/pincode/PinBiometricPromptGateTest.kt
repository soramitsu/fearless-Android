package jp.co.soramitsu.account.impl.presentation.pincode

import org.junit.Assert.*
import org.junit.Test

class PinBiometricPromptGateTest {
    @Test fun canceledOrdinaryPromptCannotAuthorizeANewStrongRecoveryPrompt() {
        val gate = PinBiometricPromptGate()
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        gate.cancel() // Fragment pause/destruction does not discard the old callback owner.
        assertEquals(PinBiometricPromptGate.Kind.NORMAL, gate.inFlight())
        assertFalse(gate.begin(PinBiometricPromptGate.Kind.STRONG_RECOVERY))
        assertNull(gate.finish()) // Late success is consumed without any authorization.
        assertNull(gate.inFlight())
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.STRONG_RECOVERY))
        assertEquals(PinBiometricPromptGate.Kind.STRONG_RECOVERY, gate.finish())
        assertNull(gate.finish())
    }

    @Test fun deviceCredentialAndNormalUnlockCannotReplaceEachOtherWhileInFlight() {
        val gate = PinBiometricPromptGate()
        gate.cancel()
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL))
        assertFalse(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        assertEquals(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL, gate.finish())
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        assertEquals(PinBiometricPromptGate.Kind.NORMAL, gate.finish())
    }
}
