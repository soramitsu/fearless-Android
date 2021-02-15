package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.RecipientSearchResult
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider
) : WalletInteractor {

    override fun assetsFlow(): Flow<List<Asset>> {
        return walletRepository.assetsFlow()
            .filter { it.isNotEmpty() }
    }

    override suspend fun syncAssetsRates(): Result<Unit> {
        return runCatching {
            walletRepository.syncAssetsRates()
        }
    }

    override suspend fun syncAssetRates(type: Token.Type): Result<Unit> {
        return kotlin.runCatching {
            walletRepository.syncAsset(type)
        }
    }

    override fun assetFlow(type: Token.Type): Flow<Asset> {
        return walletRepository.assetFlow(type)
    }

    override fun currentAssetFlow(): Flow<Asset> {
        return accountRepository.selectedAccountFlow()
            .map { Token.Type.fromNetworkType(it.network.type) }
            .flatMapLatest { assetFlow(it) }
    }

    override fun transactionsFirstPageFlow(pageSize: Int): Flow<List<Transaction>> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest {
                val accounts = accountRepository.getAccounts().map(::mapAccountToWalletAccount)
                walletRepository.transactionsFirstPageFlow(mapAccountToWalletAccount(it), pageSize, accounts)
            }
            .distinctUntilChanged { previous, new -> areTransactionPagesTheSame(previous, new) }
    }

    private fun mapAccountToWalletAccount(account: Account) = with(account) {
        WalletAccount(address, name, network)
    }

    private fun areTransactionPagesTheSame(previous: List<Transaction>, new: List<Transaction>): Boolean {
        if (previous.size != new.size) return false

        return previous.zip(new).all { (previousElement, currentElement) -> previousElement == currentElement }
    }

    override suspend fun syncTransactionsFirstPage(pageSize: Int): Result<Unit> {
        return runCatching {
            val account = accountRepository.getSelectedAccount()
            val accounts = accountRepository.getAccounts().map(::mapAccountToWalletAccount)
            walletRepository.syncTransactionsFirstPage(pageSize, mapAccountToWalletAccount(account), accounts)
        }
    }

    override suspend fun getTransactionPage(pageSize: Int, page: Int): Result<List<Transaction>> {
        return runCatching {
            val accounts = accountRepository.getAccounts().map(::mapAccountToWalletAccount)
            val myAccount = accountRepository.getSelectedAccount()
            walletRepository.getTransactionPage(pageSize, page, mapAccountToWalletAccount(myAccount), accounts)
        }
    }

    override fun selectedAccountFlow(): Flow<Account> {
        return accountRepository.selectedAccountFlow()
    }

    override suspend fun getRecipients(query: String): RecipientSearchResult {
        val account = accountRepository.getSelectedAccount()
        val contacts = walletRepository.getContacts(query)
        val myAccounts = accountRepository.getMyAccounts(query, account.network.type)

        return with(Dispatchers.Default) {
            val contactsWithoutMyAccounts = contacts - myAccounts.map { it.address }
            val myAddressesWithoutCurrent = myAccounts - account

            RecipientSearchResult(
                myAddressesWithoutCurrent.toList().map { RecipientSearchResult.Account(it.name, it.address) },
                contactsWithoutMyAccounts.toList()
            )
        }
    }

    override suspend fun validateSendAddress(address: String): Boolean {
        return accountRepository.isInCurrentNetwork(address)
    }

    override suspend fun isAddressFromPhishingList(address: String): Boolean {
        return walletRepository.isAddressFromPhishingList(address)
    }

    override suspend fun getTransferFee(transfer: Transfer): Fee {
        return walletRepository.getTransferFee(transfer)
    }

    override suspend fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel
    ): Result<Unit> {
        val validityStatus = walletRepository.checkTransferValidity(transfer)

        if (validityStatus.level > maxAllowedLevel) {
            return Result.failure(NotValidTransferStatus(validityStatus))
        }

        return runCatching {
            walletRepository.performTransfer(transfer, fee)
        }
    }

    override suspend fun checkTransferValidityStatus(transfer: Transfer): Result<TransferValidityStatus> {
        return runCatching {
            walletRepository.checkTransferValidity(transfer)
        }
    }

    override suspend fun getAccountsInCurrentNetwork(): List<Account> {
        val account = accountRepository.getSelectedAccount()

        return accountRepository.getAccountsByNetworkType(account.network.type)
    }

    override suspend fun selectAccount(address: String) {
        val account = accountRepository.getAccount(address)

        accountRepository.selectAccount(account)
    }

    override suspend fun getQrCodeSharingString(): String {
        val account = accountRepository.getSelectedAccount()

        return accountRepository.createQrAccountContent(account)
    }

    override suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<Pair<File, Asset>> {
        return runCatching {
            val file = fileProvider.getFileInExternalCacheStorage(fileName)

            file to getCurrentAsset()
        }
    }

    override suspend fun getRecipientFromQrCodeContent(content: String): Result<String> {
        return withContext(Dispatchers.Default) {
            runCatching {
                QrSharing.decode(content).address
            }
        }
    }

    override suspend fun getSelectedAccount(): Account {
        return accountRepository.getSelectedAccount()
    }

    override suspend fun getCurrentAsset(): Asset {
        val account = accountRepository.getSelectedAccount()
        val tokenType = getPrimaryTokenType(account)

        return walletRepository.getAsset(tokenType)!!
    }

    private fun getPrimaryTokenType(account: Account): Token.Type {
        return Token.Type.fromNetworkType(account.network.type)
    }
}