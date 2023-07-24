package jp.co.soramitsu.account.impl.presentation.backup_wallet

import android.widget.LinearLayout
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BackupWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val resourceManager: ResourceManager,
    private val backupService: BackupService
) : BaseViewModel(), BackupWalletCallback {

    private val walletId = savedStateHandle.get<Long>(BackupWalletDialog.ACCOUNT_ID_KEY)!!
    private val wallet = flowOf {
        accountInteractor.getMetaAccount(walletId)
    }
    private val walletItem = wallet
        .map { wallet ->

            val icon = addressIconGenerator.createAddressIcon(
                wallet.substrateAccountId,
                AddressIconGenerator.SIZE_BIG
            )

            val balanceModel = totalBalanceUseCase(walletId)

            WalletItemViewState(
                id = walletId,
                title = wallet.name,
                isSelected = false,
                walletIcon = icon,
                balance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatFiat(balanceModel.fiatSymbol)
                )
            )
        }
    private val isDeleteWalletEnabled = wallet.map { !it.isSelected }
    private val isGoogleBackupSupported = flowOf { accountInteractor.isGoogleBackupSupported(walletId) }
    private val googleBackupAddressFlow = flowOf { accountInteractor.googleBackupAddressForWallet(walletId) }
    private val refresh = MutableSharedFlow<Event<Unit>>()
    private val isAccountBackedUp = refresh.onStart { emit(Event(Unit)) }.flatMapLatest {
        println("!!! got refresh")
        googleBackupAddressFlow.map { backupService.isAccountBackedUp(it) }
    }


    val state = combine(
        walletItem,
        isDeleteWalletEnabled,
        isAccountBackedUp,
        isGoogleBackupSupported
    ) { walletItem, isDeleteWalletEnabled, isAccountBackedUp, isGoogleBackupSupported ->
        BackupWalletState(
            walletItem = walletItem,
            isWalletSavedInGoogle = isAccountBackedUp,
            isGoogleBackupSupported = isGoogleBackupSupported,
            isDeleteWalletEnabled = isDeleteWalletEnabled
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupWalletState.Empty)

    override fun onBackClick() {
        accountRouter.back()
    }

    override fun onShowMnemonicPhraseClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.getExportMnemonicDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onShowRawSeedClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.getExportSeedDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onExportJsonClick() {
        // TODO: Only polkadotChainId?
        val destination = accountRouter.openExportJsonPasswordDestination(walletId, polkadotChainId)
        accountRouter.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
    }

    override fun onDeleteGoogleBackupClick() {
//        accountRouter.openConfirmMnemonicOnExport()
//        accountRouter.openConfirmDeleteGoogleBackup()
        // TODO
        showError(
            title = resourceManager.getString(R.string.common_confirmation_title),
            message = resourceManager.getString(R.string.backup_wallet_delete_alert_message),
            positiveButtonText = resourceManager.getString(R.string.common_delete),
            negativeButtonText = resourceManager.getString(R.string.common_cancel),
            buttonsOrientation = LinearLayout.HORIZONTAL
        ) {
            println("!!! positiveClick onDeleteGoogleBackupClick")
            launch {
                googleBackupAddressFlow.firstOrNull()?.let { address ->
                    println("!!! deleting backup for address = $address")
                    backupService.deleteBackupAccount(address)
                    refresh.emit(Event(Unit))
                }
            }
        }
    }

    override fun onGoogleBackupClick() {
        launch {
//            val metaAccount = accountInteractor.selectedMetaAccount()
            val secrets = accountInteractor.getMetaAccountSecrets(walletId) ?: error("There are no secrets for walletId: $walletId")
//            val keypairSchema = secrets[MetaAccountSecrets.SubstrateKeypair]
//            val publicKey = keypairSchema[KeyPairSchema.PublicKey]
//            val privateKey = keypairSchema[KeyPairSchema.PrivateKey]
//            val nonce1 = keypairSchema[KeyPairSchema.Nonce]
//            val keypair = Keypair(publicKey, privateKey, nonce1)
//            val encryption = mapCryptoTypeToEncryption(metaAccount.substrateCryptoType)
            val entropy = secrets[MetaAccountSecrets.Entropy]
            val substrateDerivationPath = secrets[MetaAccountSecrets.SubstrateDerivationPath]
            val ethereumDerivationPath = secrets[MetaAccountSecrets.EthereumDerivationPath]
//            metaAccount.id
            val payload = CreateBackupPasswordPayload(
                mnemonic = entropy?.let { MnemonicCreator.fromEntropy(it).words }.orEmpty(),
                accountName = wallet.first().name,
                cryptoType = wallet.first().substrateCryptoType,
                substrateDerivationPath = substrateDerivationPath.orEmpty(),
                ethereumDerivationPath = ethereumDerivationPath.orEmpty(),
                createAccount = false,
                //todo update
//                substrateSeed = null,
//                substrateJson = null,
//                ethSeed = null,
//                ethJson = null
            )
            println("!!! payload.accountName = ${payload.accountName}")
            println("!!! payload.mnemonic = ${payload.mnemonic}")
            println("!!! payload.cryptoType = ${payload.cryptoType}")
            println("!!! payload.substrateDerivationPath = ${payload.substrateDerivationPath}")
            println("!!! payload.ethereumDerivationPath = ${payload.ethereumDerivationPath}")
            accountRouter.openCreateBackupPasswordDialog(payload)
        }
        // TODO
    }

    override fun onDeleteWalletClick() {
        showError(
            title = resourceManager.getString(R.string.account_delete_confirmation_title),
            message = resourceManager.getString(R.string.account_delete_confirmation_description),
            positiveButtonText = resourceManager.getString(R.string.account_delete_confirm),
            negativeButtonText = resourceManager.getString(R.string.common_cancel)
        ) {
            println("123")
            launch {
                accountInteractor.deleteAccount(walletId)
                onBackClick()
            }
        }
        // TODO
    }
}
