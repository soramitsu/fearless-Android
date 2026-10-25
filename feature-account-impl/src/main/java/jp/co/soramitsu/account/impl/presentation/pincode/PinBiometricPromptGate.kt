package jp.co.soramitsu.account.impl.presentation.pincode

/**
 * Survives fragment recreation in the PIN ViewModel. A canceled prompt owns
 * its callback slot until the platform reports its terminal result, so a late
 * ordinary unlock result can never become a strong recovery authorization.
 */
class PinBiometricPromptGate {
    enum class Kind { NORMAL, STRONG_RECOVERY, DEVICE_CREDENTIAL }
    private var active: Kind? = null
    private var canceled = false

    @Synchronized fun begin(kind: Kind): Boolean {
        if (active != null) return false
        active = kind
        canceled = false
        return true
    }

    @Synchronized fun inFlight(): Kind? = active

    @Synchronized fun cancel() { if (active != null) canceled = true }

    @Synchronized fun finish(): Kind? {
        val result = active.takeUnless { canceled }
        active = null
        canceled = false
        return result
    }
}
