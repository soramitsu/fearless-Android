package jp.co.soramitsu.crowdloan.impl.domain.contribute

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.api.data.repository.ParachainMetadata
import jp.co.soramitsu.crowdloan.api.data.repository.hasWonAuction
import jp.co.soramitsu.crowdloan.impl.data.CrowdloanSharedState
import jp.co.soramitsu.crowdloan.impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.crowdloan.impl.data.network.blockhain.extrinsic.contribute
import jp.co.soramitsu.crowdloan.impl.domain.main.Crowdloan
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.state.chainAndAsset
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection

typealias AdditionalOnChainSubmission = suspend ExtrinsicBuilder.() -> Unit

class CrowdloanContributeInteractor(
    private val extrinsicService: ExtrinsicService,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val chainStateRepository: IChainStateRepository,
    private val crowdloanSharedState: CrowdloanSharedState,
    private val crowdloanRepository: CrowdloanRepository,
    private val walletRepository: WalletRepository,
    private val moonbeamApi: MoonbeamApi,
    private val acalaApi: AcalaApi,
    private val resourceManager: ResourceManager
) {

    fun crowdloanStateFlow(
        parachainId: ParaId,
        parachainMetadata: ParachainMetadata? = null
    ): Flow<Crowdloan> = crowdloanSharedState.assetWithChain.flatMapLatest { (chain, _) ->
        val selectedMetaAccount = accountRepository.getSelectedMetaAccount()
        val accountId = selectedMetaAccount.accountId(chain)!! // TODO optional for ethereum chains

        val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis(chain.id)
        val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod(chain.id)
        val leaseOffset = crowdloanRepository.leaseOffset(chain.id)

        combine(
            crowdloanRepository.fundInfoFlow(chain.id, parachainId),
            chainStateRepository.currentBlockNumberFlow(chain.id)
        ) { fundInfo, blockNumber ->
            val contribution = crowdloanRepository.getContribution(chain.id, accountId, parachainId, fundInfo.fundIndex)
            val hasWonAuction = crowdloanRepository.hasWonAuction(chain.id, fundInfo)
            val minContribution = crowdloanRepository.minContribution(chain.id)

            mapFundInfoToCrowdloan(
                fundInfo = fundInfo,
                parachainMetadata = parachainMetadata,
                parachainId = parachainId,
                currentBlockNumber = blockNumber,
                expectedBlockTimeInMillis = expectedBlockTime,
                blocksPerLeasePeriod = blocksPerLeasePeriod,
                leaseOffset = leaseOffset,
                contribution = contribution,
                hasWonAuction = hasWonAuction,
                minContribution = minContribution
            )
        }
    }

    suspend fun estimateFee(
        parachainId: ParaId,
        contribution: BigDecimal,
        additional: AdditionalOnChainSubmission?,
        batchAll: Boolean = true,
        signature: String? = null
    ) = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = crowdloanSharedState.chainAndAsset()

        val encryption = mapCryptoTypeToEncryption(accountRepository.getSelectedAccount().cryptoType)
        val contributionInPlanks = chainAsset.planksFromAmount(contribution)
        extrinsicService.estimateFee(chain, batchAll) {
            contribute(parachainId, contributionInPlanks, signature, encryption)
            additional?.invoke(this)
        }
    }

    suspend fun contribute(
        parachainId: ParaId,
        contribution: BigDecimal,
        additional: AdditionalOnChainSubmission?,
        batchAll: Boolean = true,
        signature: String? = null
    ) = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = crowdloanSharedState.chainAndAsset()
        val selectedMetaAccount = accountRepository.getSelectedMetaAccount()

        val accountId = selectedMetaAccount.accountId(chain)!!
        val contributionInPlanks = chainAsset.planksFromAmount(contribution)
        val encryption = mapCryptoTypeToEncryption(accountRepository.getSelectedAccount().cryptoType)

        extrinsicService.submitExtrinsic(chain, accountId, batchAll) {
            contribute(parachainId, contributionInPlanks, signature, encryption)
            additional?.invoke(this)
        }.getOrThrow()
    }

    suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel,
        additional: AdditionalOnChainSubmission?,
        batchAll: Boolean = true
    ): Result<Unit> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
        val accountId = metaAccount.accountId(chain)!!

        val validityStatus = walletRepository.checkTransferValidity(metaAccount.id, accountId, chain, transfer, additional, batchAll)

        if (validityStatus.level > maxAllowedLevel) {
            return Result.failure(NotValidTransferStatus(validityStatus))
        }

        return runCatching {
            walletRepository.performTransfer(accountId, chain, transfer, fee, null, additional, batchAll)
        }
    }

    suspend fun getHealth(apiUrl: String, apiKey: String) = try {
        moonbeamApi.getHealth(apiUrl, apiKey)
        true
    } catch (e: Throwable) {
        val errorCode = (e as? HttpException)?.response()?.code()
        if (errorCode == HttpURLConnection.HTTP_FORBIDDEN) {
            false
        } else {
            throw transformException(e)
        }
    }

    suspend fun getAcalaStatement(apiUrl: String) = acalaApi.getStatement(apiUrl)

    private fun transformException(exception: Throwable): BaseException = when (exception) {
        is HttpException -> {
            val response = exception.response()!!

            val errorCode = response.code()
            response.errorBody()?.close()

            BaseException.httpError(errorCode, resourceManager.getString(R.string.common_undefined_error_message))
        }
        is IOException -> BaseException.networkError(resourceManager.getString(R.string.connection_error_message), exception)
        else -> BaseException.unexpectedError(exception)
    }

    suspend fun saveEthAddress(paraId: ParaId, address: String, etheriumAddress: String) {
        crowdloanRepository.saveEthAddress(paraId, address, etheriumAddress)
    }
}
