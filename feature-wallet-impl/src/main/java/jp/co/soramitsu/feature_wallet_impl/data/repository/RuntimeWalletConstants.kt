package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import java.math.BigInteger

class RuntimeWalletConstants(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) : WalletConstants {

    override suspend fun existentialDeposit(): BigInteger {
        val runtime = runtimeProperty.get()

        return runtime.metadata.module("Balances").numberConstant("ExistentialDeposit", runtime)
    }
}
