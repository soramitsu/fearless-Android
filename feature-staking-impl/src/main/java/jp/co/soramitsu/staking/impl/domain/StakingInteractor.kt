package jp.co.soramitsu.staking.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntimeOrNull
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class StakingInteractor(
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val stakingSharedState: StakingSharedState,
    private val chainStateRepository: IChainStateRepository,
    private val chainRegistry: ChainRegistry,
    private val addressIconGenerator: AddressIconGenerator,
    private val walletRepository: WalletRepository
) {
    suspend fun getCurrentMetaAccount() = accountRepository.getSelectedMetaAccount()
    fun selectedMetaAccountFlow() = accountRepository.selectedMetaAccountFlow()
    suspend fun getMetaAccount(metaId: Long) = accountRepository.getMetaAccount(metaId)

    suspend fun syncStakingRewards(chainId: ChainId, accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.sync(chainId, accountAddress)
        }
    }

    fun selectedChainFlow() = stakingSharedState.assetWithChain.map { it.chain }

    fun selectionStateFlow() = combineToPair(
        accountRepository.selectedMetaAccountFlow(),
        stakingSharedState.assetWithChain
    ).debounce(100)

    suspend fun getAccountProjectionsInSelectedChains() = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()

        accountRepository.allMetaAccounts().mapNotNull {
            mapAccountToStakingAccount(chain, it)
        }
    }

    fun currentAssetFlow() = stakingSharedState.currentAssetFlow()

    suspend fun getUtilityAsset(chainId: ChainId): jp.co.soramitsu.wallet.impl.domain.model.Asset? {
        val chain = getChain(chainId)
        return getUtilityAsset(chain)
    }

    suspend fun getUtilityAsset(chain: Chain): jp.co.soramitsu.wallet.impl.domain.model.Asset? {
        val currentAccount = getCurrentMetaAccount()
        return walletRepository.getAsset(
            currentAccount.id,
            requireNotNull(currentAccount.accountId(chain)),
            requireNotNull(chain.utilityAsset),
            null
        )
    }

    fun selectedAccountProjectionFlow(): Flow<StakingAccount> {
        return combine(
            stakingSharedState.assetWithChain,
            accountRepository.selectedMetaAccountFlow().debounce(100)
        ) { (chain, _), account ->
            mapAccountToStakingAccount(chain, account)
        }.mapNotNull { it }
    }

    suspend fun getProjectedAccount(address: String): StakingAccount = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()
        val accountId = chain.accountIdOf(address)

        val metaAccount = accountRepository.findMetaAccount(accountId)

        StakingAccount(
            address = address,
            name = metaAccount?.name,
            chain.isEthereumBased
        )
    }

    suspend fun getSelectedAccountProjection(): StakingAccount? = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()
        val metaAccount = accountRepository.getSelectedMetaAccount()

        mapAccountToStakingAccount(chain, metaAccount)
    }

    suspend fun currentBlockNumber(): BlockNumber {
        return chainStateRepository.currentBlock(getSelectedChain().id)
    }

    suspend fun getChain(chainId: ChainId): Chain {
        return chainRegistry.getChain(chainId)
    }

    suspend fun getChainMetadata(chainId: ChainId): RuntimeMetadata {
        return chainRegistry.getRuntime(chainId).metadata
    }

    suspend fun feeValidation(): SetupStakingFeeValidation {
        val asset = currentAssetFlow().first()

        return EnoughToPayFeesValidation(
            feeExtractor = { it.maxFee },
            availableBalanceProducer = { asset.availableForStaking },
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = { it.bondAmount ?: BigDecimal.ZERO }
        )
    }

    suspend fun getAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
        val isEthereumBased = selectedChainFlow().first().isEthereumBased
        return if (isEthereumBased) {
            addressIconGenerator.createEthereumAddressModel(accountAddress, sizeInDp, accountName)
        } else {
            addressIconGenerator.createAddressModel(accountAddress, sizeInDp, accountName)
        }
    }

    suspend fun getWalletAddressModel(sizeInDp: Int): AddressModel {
        val polkadotChain = chainRegistry.getChain(polkadotChainId)
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val addressInPolkadot = metaAccount.address(polkadotChain) ?: error("Cannot find an address")
        return addressIconGenerator.createAddressModel(addressInPolkadot, sizeInDp, metaAccount.name)
    }

    suspend fun existentialDeposit(chainId: ChainId): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
    }

    suspend fun getAccountBalance(chainId: ChainId, accountId: AccountId): AccountInfo {
        return stakingRepository.getAccountInfo(chainId, accountId)
    }

    suspend fun getStashBalance(stashId: AccountId, configuration: Asset): BigDecimal {
        val stashMetaAccount = accountRepository.findMetaAccount(stashId)
        val cachedBalance = stashMetaAccount?.let { walletRepository.getAsset(stashMetaAccount.id, stashId, configuration, null)?.availableForStaking }
        return if (cachedBalance == null) {
            val stashInfo = stakingRepository.getAccountInfo(configuration.chainId, stashId).data
            val availableForStakingInPlanks = stashInfo.free - stashInfo.feeFrozen
            configuration.amountFromPlanks(availableForStakingInPlanks)
        } else {
            cachedBalance
        }
    }

    suspend fun checkControllerDeprecations(metaAccount: MetaAccount, chain: Chain): ControllerDeprecationWarning? {
        val isControllerAccountDeprecated =
            chainRegistry.getRuntimeOrNull(chain.id)?.metadata?.module(Modules.STAKING)?.calls?.get("set_controller")?.arguments?.isEmpty() == true
        if (!isControllerAccountDeprecated) return null

        val accountId = metaAccount.accountId(chain) ?: return null

        // checking current account is stash

        val controllerAccount = walletRepository.getControllerAccount(chain.id, accountId)

        val currentAccountIsStashAndController = controllerAccount != null && controllerAccount.contentEquals(accountId) // the case is resolved
        if (currentAccountIsStashAndController) {
            return null
        }

        val currentAccountHasAnotherController = controllerAccount != null && !controllerAccount.contentEquals(accountId) // user needs to fix it
        if (currentAccountHasAnotherController) {
            return ControllerDeprecationWarning.ChangeController(chain.id, chain.name)
        }

        // checking current account is controller
        val currentAccountIsNotStash = controllerAccount == null

        if (currentAccountIsNotStash) {
            val stash = walletRepository.getStashAccount(chain.id, accountId)
            // we've found the stash
            if (stash != null) {
                return ControllerDeprecationWarning.ImportStash(chain.id, stash.toAddress(chain.addressPrefix.toShort()))
            }
        }

        return null
    }
}
