package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.feature_account_api.data.mappers.stubNetwork
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_account_api.domain.model.addressIn
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.OperationsPageChange
import jp.co.soramitsu.feature_wallet_api.domain.model.RecipientSearchResult
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val fileProvider: FileProvider,
) : WalletInteractor {

    override fun assetsFlow(): Flow<List<Asset>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { walletRepository.assetsFlow(it.id) }
            .filter { it.isNotEmpty() }
    }

    override suspend fun syncAssetsRates(): Result<Unit> {
        return runCatching {
            walletRepository.syncAssetsRates()
        }
    }

    override fun assetFlow(chainId: ChainId, chainAssetId: Int): Flow<Asset> {
        return accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->
            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountIdIn(chain)!!

            walletRepository.assetFlow(accountId, chainAsset)
        }
    }

    override suspend fun getCurrentAsset(chainId: ChainId, chainAssetId: Int): Asset {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)

        return walletRepository.getAsset(metaAccount.accountIdIn(chain)!!, chainAsset)!!
    }

    override fun operationsFirstPageFlow(chainId: ChainId, chainAssetId: Int): Flow<OperationsPageChange> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest { metaAccount ->
                val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
                val accountId = metaAccount.accountIdIn(chain)!!

                walletRepository.operationsFirstPageFlow(accountId, chain, chainAsset).withIndex().map { (index, cursorPage) ->
                    OperationsPageChange(cursorPage, accountChanged = index == 0)
                }
            }
    }

    override suspend fun syncOperationsFirstPage(
        chainId: ChainId,
        chainAssetId: Int,
        pageSize: Int,
        filters: Set<TransactionFilter>,
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountIdIn(chain)!!

            walletRepository.syncOperationsFirstPage(pageSize, filters, accountId, chain, chainAsset)
        }
    }

    override suspend fun getOperations(
        chainId: ChainId,
        chainAssetId: Int,
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
    ): Result<CursorPage<Operation>> {
        return runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val (chain, chainAsset) = chainRegistry.chainWithAsset(chainId, chainAssetId)
            val accountId = metaAccount.accountIdIn(chain)!!

            walletRepository.getOperations(
                pageSize,
                cursor,
                filters,
                accountId,
                chain,
                chainAsset
            )
        }
    }

    override fun selectedAccountFlow(chainId: ChainId): Flow<WalletAccount> {
        return accountRepository.selectedMetaAccountFlow()
            .map { metaAccount ->
                val chain = chainRegistry.getChain(chainId)

                mapAccountToWalletAccount(chain, metaAccount)
            }
    }

    // TODO wallet
    override suspend fun getRecipients(query: String, chainId: ChainId): RecipientSearchResult {
//        val metaAccount = accountRepository.getSelectedMetaAccount()
//        val chain = chainRegistry.getChain(chainId)
//        val accountId = metaAccount.accountIdIn(chain)!!
//
//        val contacts = walletRepository.getContacts(accountId, chain, query)
//        val myAccounts = accountRepository.getMyAccounts(query, chain.id)
//
//        return withContext(Dispatchers.Default) {
//            val contactsWithoutMyAccounts = contacts - myAccounts.map { it.address }
//            val myAddressesWithoutCurrent = myAccounts - metaAccount
//
//            RecipientSearchResult(
//                myAddressesWithoutCurrent.toList().map { mapAccountToWalletAccount(chain, it) },
//                contactsWithoutMyAccounts.toList()
//            )
//        }

        return RecipientSearchResult(
            myAccounts = emptyList(),
            contacts = emptyList()
        )
    }

    override suspend fun validateSendAddress(chainId: ChainId, address: String): Boolean = withContext(Dispatchers.Default) {
        val chain = chainRegistry.getChain(chainId)

        chain.isValidAddress(address)
    }

    // TODO wallet phishing
    override suspend fun isAddressFromPhishingList(address: String): Boolean {
        return /*walletRepository.isAccountIdFromPhishingList(address)*/ false
    }

    // TODO wallet fee
    override suspend fun getTransferFee(transfer: Transfer): Fee {
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)

        return walletRepository.getTransferFee(chain, transfer)
    }

    override suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel,
    ): Result<Unit> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
        val accountId = metaAccount.accountIdIn(chain)!!

        val validityStatus = walletRepository.checkTransferValidity(accountId, chain, transfer)

        if (validityStatus.level > maxAllowedLevel) {
            return Result.failure(NotValidTransferStatus(validityStatus))
        }

        return runCatching {
            walletRepository.performTransfer(accountId, chain, transfer, fee)
        }
    }

    override suspend fun checkTransferValidityStatus(transfer: Transfer): Result<TransferValidityStatus> {
        return runCatching {
            val metaAccount = accountRepository.getSelectedMetaAccount()
            val chain = chainRegistry.getChain(transfer.chainAsset.chainId)
            val accountId = metaAccount.accountIdIn(chain)!!

            walletRepository.checkTransferValidity(accountId, chain, transfer)
        }
    }

    // TODO wallet receive
    override suspend fun getQrCodeSharingString(): String {
        val account = accountRepository.getSelectedAccount()

        return accountRepository.createQrAccountContent(account)
    }

    // TODO just create file, screens can retrieve asset with getCurrentAsset()
    override suspend fun createFileInTempStorageAndRetrieveAsset(
        chainId: ChainId,
        chainAssetId: Int,
        fileName: String
    ): Result<Pair<File, Asset>> {
        return runCatching {
            val file = fileProvider.getFileInExternalCacheStorage(fileName)

            file to getCurrentAsset(chainId, chainAssetId)
        }
    }

    override suspend fun getRecipientFromQrCodeContent(content: String): Result<String> {
        return withContext(Dispatchers.Default) {
            runCatching {
                QrSharing.decode(content).address
            }
        }
    }

    private fun mapAccountToWalletAccount(chain: Chain, account: MetaAccount) = with(account) {
        WalletAccount(account.addressIn(chain)!!, name, stubNetwork(chain.id))
    }
}
