package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import androidx.lifecycle.LiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter

private const val AVATAR_SIZE_DP = 32

class ReceiveViewModel(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter
) : BaseViewModel() {

    private val selectedAccountObservable = interactor.observeSelectedAccount()

    val qrBitmapLiveData = getQrCodeSharingString()
        .asLiveData()

    val accountLiveData = getAccountAddress()
        .asLiveData()

    val accountIconLiveData: LiveData<AddressModel> = observeIcon(selectedAccountObservable)
        .asLiveData()

    fun addressCopyClicked() {
        accountLiveData.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    fun backClicked() {
        router.back()
    }

    fun shareButtonClicked() {
        // TODO: implement sharing here
    }

    private fun getQrCodeSharingString() = interactor.getQrCodeSharingString()
        .subscribeOn(Schedulers.io())
        .map { qrCodeGenerator.generateQrBitmap(it) }
        .observeOn(AndroidSchedulers.mainThread())

    private fun getAccountAddress() = selectedAccountObservable
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    private fun observeIcon(accountObservable: Observable<Account>): Observable<AddressModel> {
        return accountObservable
            .subscribeOn(Schedulers.io())
            .flatMapSingle { account ->
                interactor.getAddressId(account.address).flatMap { accountId ->
                    addressIconGenerator.createAddressModel(account.address, accountId, AVATAR_SIZE_DP)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
    }
}