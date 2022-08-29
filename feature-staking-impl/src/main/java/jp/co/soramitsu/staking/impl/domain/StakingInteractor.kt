package jp.co.soramitsu.staking.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
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
    private val chainStateRepository: ChainStateRepository,
    private val chainRegistry: ChainRegistry,
    private val addressIconGenerator: AddressIconGenerator
) {
    suspend fun syncStakingRewards(chainId: ChainId, accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.sync(chainId, accountAddress)
        }
    }

    fun selectedChainFlow() = stakingSharedState.assetWithChain.map { it.chain }

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return stakingRepository.stakingStoriesFlow()
    }

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

    suspend fun feeValidation(): SetupStakingFeeValidation {
        val asset = currentAssetFlow().first()

        return EnoughToPayFeesValidation(
            feeExtractor = { it.maxFee },
            availableBalanceProducer = { asset.transferable },
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
}
