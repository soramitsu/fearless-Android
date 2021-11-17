package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.hasWonAuction
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic.contribute
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import retrofit2.HttpException

typealias AdditionalOnChainSubmission = suspend ExtrinsicBuilder.() -> Unit

class CrowdloanContributeInteractor(
    private val extrinsicService: ExtrinsicService,
    private val feeEstimator: FeeEstimator,
    private val accountRepository: AccountRepository,
    private val chainStateRepository: ChainStateRepository,
    private val crowdloanRepository: CrowdloanRepository,
    private val walletRepository: WalletRepository,
    private val moonbeamApi: MoonbeamApi,
    private val acalaApi: AcalaApi,
    private val resourceManager: ResourceManager,
) {

    fun crowdloanStateFlow(
        parachainId: ParaId,
        parachainMetadata: ParachainMetadata? = null
    ): Flow<Crowdloan> = accountRepository.selectedNetworkTypeFlow().flatMapLatest {
        val accountAddress = accountRepository.getSelectedAccount().address

        val expectedBlockTime = chainStateRepository.expectedBlockTimeInMillis()
        val blocksPerLeasePeriod = crowdloanRepository.blocksPerLeasePeriod()

        combine(
            crowdloanRepository.fundInfoFlow(parachainId, it),
            chainStateRepository.currentBlockNumberFlow(it)
        ) { fundInfo, blockNumber ->
            val contribution = crowdloanRepository.getContribution(accountAddress.toAccountId(), parachainId, fundInfo.trieIndex)
            val hasWonAuction = crowdloanRepository.hasWonAuction(fundInfo)

            mapFundInfoToCrowdloan(
                fundInfo = fundInfo,
                parachainMetadata = parachainMetadata,
                parachainId = parachainId,
                currentBlockNumber = blockNumber,
                expectedBlockTimeInMillis = expectedBlockTime,
                blocksPerLeasePeriod = blocksPerLeasePeriod,
                contribution = contribution,
                hasWonAuction = hasWonAuction
            )
        }
    }

    suspend fun estimateFee(
        parachainId: ParaId,
        contribution: BigDecimal,
        token: Token,
        additional: AdditionalOnChainSubmission?,
        signature: String? = null,
    ) = withContext(Dispatchers.Default) {
        val contributionInPlanks = token.planksFromAmount(contribution)

        val encryption = mapCryptoTypeToEncryption(accountRepository.getSelectedAccount().cryptoType)
        val feeInPlanks = feeEstimator.estimateFee(accountRepository.getSelectedAccount().address, true) {
            contribute(parachainId, contributionInPlanks, signature, encryption)
            additional?.invoke(this)
        }

        token.amountFromPlanks(feeInPlanks)
    }

    suspend fun contribute(
        originAddress: String,
        parachainId: ParaId,
        contribution: BigDecimal,
        token: Token,
        additional: AdditionalOnChainSubmission?,
        signature: String? = null,
    ) = withContext(Dispatchers.Default) {
        val contributionInPlanks = token.planksFromAmount(contribution)

        val encryption = mapCryptoTypeToEncryption(accountRepository.getSelectedAccount().cryptoType)
        extrinsicService.submitExtrinsic(originAddress, true) {
            contribute(parachainId, contributionInPlanks, signature, encryption)

            additional?.invoke(this)
        }.getOrThrow()
    }

    suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel,
        additional: AdditionalOnChainSubmission?,
    ): Result<Unit> {
        val accountAddress = accountRepository.getSelectedAccount().address
        val validityStatus = walletRepository.checkTransferValidity(accountAddress, transfer, additional)

        if (validityStatus.level > maxAllowedLevel) {
            return Result.failure(NotValidTransferStatus(validityStatus))
        }

        return runCatching {
            walletRepository.performTransfer(accountAddress, transfer, fee, additional)
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
