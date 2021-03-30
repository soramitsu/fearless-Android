package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.Function
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExtrinsicBuilderInteractor @Inject constructor(
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    private val substrateCalls: SubstrateCalls,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) {

    fun currentAssetFlow() = accountRepository.selectedAccountFlow()
        .flatMapLatest { walletRepository.assetsFlow(it.address) }
        .filter { it.isNotEmpty() }
        .map { it.first() }

    suspend fun modules() : List<String> {
        return runtime().metadata.modules.values.filter { !it.calls.isNullOrEmpty() }
            .map { it.name }
            .sorted()
            .toList()
    }

    suspend fun calls(module: String) : List<String> {
        return runtime().metadata.module(module).calls.orEmpty().keys.sorted().toList()
    }

    suspend fun call(moduleName: String, callName: String) : Function {
        return runtime().metadata.module(moduleName).call(callName)
    }

    suspend fun callInstance(moduleName: String, callName: String, arguments: Map<String, Any?>) : GenericCall.Instance {
        val runtimeMetadata =  runtime().metadata
        val (moduleIndex, callIndex) = runtimeMetadata.module(moduleName).call(callName).index

        return GenericCall.Instance(moduleIndex, callIndex, arguments)
    }

    suspend fun runtime() = runtimeProperty.get()

    suspend fun send(call: GenericCall.Instance) = withContext(Dispatchers.Default) {
        val extrinsic = extrinsicBuilderFactory.create(accountRepository.getSelectedAccount().address)
            .call(call.moduleIndex, call.callIndex, call.arguments)
            .build()

        substrateCalls.submitExtrinsic(extrinsic)
    }
}
