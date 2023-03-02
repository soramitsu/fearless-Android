package jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.signWithAccount
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.RemarkStoreRequest
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.RemarkVerifyRequest
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.SignatureRequest
import jp.co.soramitsu.crowdloan.impl.data.network.blockhain.extrinsic.addMemo
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
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
    private val chainRegistry: ChainRegistry
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    private var termsHash: String? = null
    private var termsSigned: String? = null
    private var moonbeamRemark: String? = null
    private var remarkTxHash: String? = null

    fun getRemarkTxHash(): String = remarkTxHash.orEmpty()

    suspend fun getContributionSignature(apiUrl: String, apiKey: String, contribution: BigInteger, paraId: ParaId, chainId: ChainId): String {
        val accountId = accountRepository.getSelectedAccount(chainId).accountId
        val fundInfo = crowdloanRepository.fundInfoFlow(chainId, paraId).first()
        val prevContribution = crowdloanRepository.getContribution(chainId, accountId, paraId, fundInfo.fundIndex)
        val randomGuid = ByteArray(10) { (0..20).random().toByte() }.toHexString(false)
        val response = runCatching {
            moonbeamApi.makeSignature(
                apiUrl,
                apiKey,
                SignatureRequest(
                    accountRepository.getSelectedAccount(chainId).address,
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

    suspend fun doSystemRemark(apiUrl: String, apiKey: String, chainId: ChainId): Boolean {
        val remark = requireNotNull(moonbeamRemark)
        val result = extrinsicService.submitAndWatchExtrinsic(
            chain = chainRegistry.getChain(chainId),
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
        return if (result != null) {
            remarkTxHash = result.first
            val verify = runCatching {
                moonbeamApi.verifyRemark(
                    apiUrl,
                    apiKey,
                    RemarkVerifyRequest(
                        accountRepository.getSelectedAccount().address,
                        result.second,
                        result.first
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

    suspend fun getSystemRemarkFee(apiUrl: String, apiKey: String, chainId: ChainId): BigInteger {
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
        val chain = chainRegistry.getChain(chainId)
        return extrinsicService.estimateFee(
            chain,
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

    suspend fun getTerms(url: String, chainId: ChainId): String {
        return httpExceptionHandler.wrap { moonbeamApi.getTerms(url) }.also {
            calcHashes(digest.digest(it.encodeToByteArray()), chainId)
        }
    }

    private suspend fun calcHashes(termsBytes: ByteArray, chainId: ChainId) {
        val account = accountRepository.getSelectedAccount(chainId)
        termsHash = termsBytes.toHexString(false)
        termsSigned = accountRepository.signWithAccount(account, termsHash?.encodeToByteArray()!!).toHexString(true)
    }

    fun getEthAddress(paraId: ParaId, address: String): String? {
        return crowdloanRepository.getEthAddress(paraId, address)
    }
}
