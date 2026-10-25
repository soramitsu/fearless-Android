package jp.co.soramitsu.account.impl.domain.recovery

/** A replacement cannot be installed for the recorded public identity. */
class WalletRecoveryIdentityMismatch : IllegalArgumentException("Recovery material does not match this account")
