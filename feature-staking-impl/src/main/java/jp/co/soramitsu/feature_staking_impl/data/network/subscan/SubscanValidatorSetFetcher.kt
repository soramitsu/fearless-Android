package jp.co.soramitsu.feature_staking_impl.data.network.subscan

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest.Companion.BOND
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest.Companion.MODULE_STAKING
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest.Companion.MODULE_UTILITY
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest.Companion.NOMINATE
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest.Companion.SET_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.ExtrinsicRemote
import jp.co.soramitsu.feature_staking_impl.data.repository.SubscanPagedSynchronizer
import jp.co.soramitsu.feature_staking_impl.data.repository.fetchAll
import jp.co.soramitsu.feature_staking_impl.data.repository.subscanCollectionFetcher

internal open class CallArg<T>(
    val name: String,
    val type: String,
    val value: T,
)

internal class BatchCallArg(
    name: String,
    type: String,
    value: List<CallDescription>,
) : CallArg<List<CallDescription>>(name, type, value)

internal typealias BatchParams = List<BatchCallArg>

internal val BatchParams.calls: List<CallDescription>
    get() = first().value

internal typealias GenericParams = List<CallArg<Any?>>

internal fun GenericParams.argumentValue(name: String): Any? = firstOrNull { it.name == name }?.value

internal class CallDescription(
    @SerializedName("call_args")
    val callArgs: List<CallArg<Any?>>,
    @SerializedName("call_function")
    val callFunction: String,
    @SerializedName("call_index")
    val callIndex: String,
    @SerializedName("call_module")
    val callModule: String,
)

class SubscanValidatorSetFetcher(
    private val gson: Gson,
    private val stakingApi: StakingApi,
    private val subscanPagedSynchronizer: SubscanPagedSynchronizer,
) {

    suspend fun fetchAllValidators(stashAccountAddress: String): List<String> {
        val stashUtilityExtrinsics = fetchExtrinsics(stashAccountAddress, module = MODULE_UTILITY)
        val stashBondExtrinsics = fetchExtrinsics(stashAccountAddress, module = MODULE_STAKING, call = BOND)
        val stashSetControllerExtrinsics = fetchExtrinsics(stashAccountAddress, module = MODULE_STAKING, call = SET_CONTROLLER)

        val controllersFromBond = tryExtractCallArgumentFromExtrinsics(
            extrinsics = stashBondExtrinsics,
            callName = BOND,
            argumentName = "controller",
            extractor = ::extractAccountIdFromArgument
        )
        val controllersFromSetController = tryExtractCallArgumentFromExtrinsics(
            extrinsics = stashSetControllerExtrinsics,
            callName = SET_CONTROLLER,
            argumentName = "controller",
            extractor = ::extractAccountIdFromArgument
        )
        val controllersFromBatches = extractControllerChangesFromBatches(onlyBatches(stashUtilityExtrinsics))

        val networkType = stashAccountAddress.networkType()

        val allControllers = (controllersFromBond + controllersFromSetController + controllersFromBatches)
            .filterNotNull()
            .distinct()
            .map { controllerIdHex -> accountIdToAddress(controllerIdHex, networkType) }

        return allControllers.map { controllerAddress ->
            val controllerUtilityExtrinsics = if (stashAccountAddress == controllerAddress) {
                stashUtilityExtrinsics
            } else {
                fetchExtrinsics(controllerAddress, module = MODULE_UTILITY)
            }
            val nominateExtrinsics = fetchExtrinsics(controllerAddress, module = MODULE_STAKING, call = NOMINATE)

            val validatorsFromNominate = tryExtractCallArgumentFromExtrinsics(
                extrinsics = nominateExtrinsics,
                callName = NOMINATE,
                argumentName = "targets",
                extractor = ::extractNominationsFromArgument
            )
            val validatorsFromBatches = onlyBatches(controllerUtilityExtrinsics).mapNotNull {
                tryExtractCallArgumentFromBatch(it, NOMINATE, "targets", ::extractNominationsFromArgument)
            }

            (validatorsFromNominate + validatorsFromBatches).flatten()
        }
            .flatten()
            .distinct()
            .map { validatorIdHex -> accountIdToAddress(validatorIdHex, networkType) }
    }

    private fun accountIdToAddress(accountIdHex: String, networkType: Node.NetworkType) = accountIdHex.fromHex().toAddress(networkType)

    private fun extractControllerChangesFromBatches(batches: List<BatchParams>): List<String> {
        val controllersFromBond = batches.mapNotNull {
            tryExtractCallArgumentFromBatch(it, BOND, "controller", ::extractAccountIdFromArgument)
        }

        val controllersFromSetController = batches.mapNotNull {
            tryExtractCallArgumentFromBatch(it, SET_CONTROLLER, "controller", ::extractAccountIdFromArgument)
        }

        return controllersFromSetController + controllersFromBond
    }

    private fun onlyBatches(extrinsics: List<ExtrinsicRemote>): List<BatchParams> {
        val batchCalls = listOf(ExtrinsicHistoryRequest.CALL_BATCH, ExtrinsicHistoryRequest.CALL_BATCH_ALL)

        return extrinsics.filter { it.callModuleFunction in batchCalls }
            .map { extrinsic ->
                gson.fromJson(extrinsic.params, typeToken<BatchParams>())
            }
    }

    private fun <T> tryExtractCallArgumentFromExtrinsics(
        extrinsics: List<ExtrinsicRemote>,
        callName: String,
        argumentName: String,
        extractor: (Any?) -> T,
    ): List<T> {
        return extrinsics.filter { it.callModuleFunction == callName }
            .mapNotNull {
                val callParams: GenericParams = gson.fromJson(it.params, typeToken<GenericParams>())

                callParams.argumentValue(argumentName)?.let(extractor)
            }
    }

    private inline fun <reified T> typeToken() = object : TypeToken<T>() {}.type

    private fun <T> tryExtractCallArgumentFromBatch(
        batch: BatchParams,
        call: String,
        argumentName: String,
        extractor: (Any?) -> T,
    ): T? {
        return batch.calls.filter { it.callFunction == call }
            .mapNotNull { callDescription ->
                val argument = callDescription.callArgs.firstOrNull { it.name == argumentName }

                argument?.let { extractor(argument.value) }
            }.lastOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAccountIdFromArgument(argument: Any?): String? = when (argument) {
        // MultiAddress
        is Map<*, *> -> {
            val casted = argument as Map<String, Any>

            casted[MultiAddress.TYPE_ID] as? String
        }
        // AccountId
        is String -> argument
        else -> null
    }

    private fun extractNominationsFromArgument(argument: Any?): List<String> {
        require(argument is List<*>)

        return argument.mapNotNull(::extractAccountIdFromArgument)
    }

    private suspend fun fetchExtrinsics(accountAddress: String, module: String, call: String? = null) = subscanPagedSynchronizer.fetchAll(
        pageFetcher = subscanCollectionFetcher { page, row ->
            val request = ExtrinsicHistoryRequest(page, row, accountAddress, module, call)

            stakingApi.getExtrinsicHistory(accountAddress.networkType().subscanSubDomain(), request)
        }
    )
}
