package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.RemarkStoreRequest
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.RemarkVerifyRequest
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.SignatureRequest
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic.addMemo
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.MessageDigest

class MoonbeamContributeInteractor(
    private val moonbeamApi: MoonbeamApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val crowdloanRepository: CrowdloanRepository,
    private val extrinsicService: ExtrinsicService,
    private val snapshot: SuspendableProperty<RuntimeSnapshot>,
    private val chainRegistry: ChainRegistry,
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    private var termsHash: String? = null
    private var termsSigned: String? = null
    private var moonbeamRemark: String? = null
    private var remarkTxHash: String? = null

    fun getRemarkTxHash(): String = remarkTxHash.orEmpty()

    suspend fun getContributionSignature(apiUrl: String, apiKey: String, contribution: BigInteger, paraId: ParaId): String {
        val chainId = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        val address = accountRepository.getSelectedAccount().address
        val fundInfo = crowdloanRepository.fundInfoFlow(chainId, paraId).first()
        val prevContribution = crowdloanRepository.getContribution(chainId, address.toAccountId(), paraId, fundInfo.trieIndex)
        val randomGuid = ByteArray(10) { (0..20).random().toByte() }.toHexString(false)
        val response = runCatching {
            moonbeamApi.makeSignature(
                apiUrl,
                apiKey,
                SignatureRequest(
                    accountRepository.getSelectedAccount().address,
                    contribution.toString(),
                    prevContribution?.amount?.toString() ?: "0",
                    randomGuid
                )
            ).signature
        }
        return response.getOrDefault("")
    }

    suspend fun submitMemo(
        paraId: ParaId,
        ethereumAddress: String,
        extrinsicBuilder: ExtrinsicBuilder
    ) {
        withContext(Dispatchers.Default) {
            extrinsicBuilder.addMemo(paraId, ethereumAddress.fromHex())
        }
    }

    suspend fun doSystemRemark(apiUrl: String, apiKey: String): Boolean {
        val remark = requireNotNull(moonbeamRemark)
        val result = extrinsicService.submitAndWatchExtrinsic(
            chain = chainRegistry.getChain("91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"),
            accountAddress = accountRepository.getSelectedAccount().address,
            formExtrinsic = {
                call(
                    moduleName = "System",
                    callName = "remark",
                    arguments = mapOf(
                        "remark" to remark.toByteArray()
                    )
                )
            },
            snapshot = snapshot.get()
        )
        return if (result != null) {
            remarkTxHash = result.first
            val verify = runCatching {
                moonbeamApi.verifyRemark(
                    apiUrl, apiKey,
                    RemarkVerifyRequest(
                        accountRepository.getSelectedAccount().address,
                        result.second, result.first
                    )
                ).verified
            }
                .getOrElse {
                    false
                }
            verify
        } else {
            false
        }
    }

    suspend fun getSystemRemarkFee(apiUrl: String, apiKey: String): BigInteger {
        val sign = requireNotNull(termsSigned)
        val remarkResponse = moonbeamApi.agreeRemark(
            apiUrl,
            apiKey,
            RemarkStoreRequest(
                accountRepository.getSelectedAccount().address,
                sign
            )
        )
        val remark = remarkResponse.remark
        moonbeamRemark = remark
        val polkadot = chainRegistry.getChain("91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3")
        return extrinsicService.estimateFee(
            polkadot,
            formExtrinsic = {
                call(
                    moduleName = "System",
                    callName = "remark",
                    arguments = mapOf(
                        "remark" to remark.toByteArray()
                    )
                )
            }
        )
    }

    suspend fun getTerms(url: String): String {
        return httpExceptionHandler.wrap { moonbeamApi.getTerms(url) }.also {
            calcHashes(digest.digest(it.encodeToByteArray()))
        }
    }

    private suspend fun calcHashes(termsBytes: ByteArray) {
        val account = accountRepository.getSelectedAccount()
        termsHash = termsBytes.toHexString(false)
        termsSigned = accountRepository.signWithAccount(account, termsHash?.encodeToByteArray()!!).toHexString(true)
    }

    fun getEthAddress(paraId: ParaId, address: String): String? {
        return crowdloanRepository.getEthAddress(paraId, address)
    }
}
