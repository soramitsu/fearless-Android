package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.validators.current.search.BlockedValidatorException
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorStakeParcelModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

class ProducerSearchScreenActionTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun backClosesWithoutApplyingAnotherSelection() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.model.backClicked()
            verify(f.router).back()
            verify(f.producer, never()).blockProducerSelected(any(), any(), any())
        } finally {
            f.close()
        }
    }

    @Test fun doneClosesWithoutApplyingAnotherSelection() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.model.doneClicked()
            verify(f.router).back()
            verify(f.producer, never()).blockProducerSelected(any(), any(), any())
        } finally {
            f.close()
        }
    }

    @Test fun tappingARowUsesItsExactAddressAndOwnedSelectionProcess() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.model.blockProducerClicked(f.row(1))
            verify(f.producer).blockProducerSelected(f.addresses[1], f.data.process, f.data.lifecycle)
        } finally {
            f.close()
        }
    }

    @Test fun successfulSelectionDoesNotPublishAnError() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            f.model.blockProducerClicked(f.row())
            assertNull(f.model.errorLiveData.value)
        } finally {
            f.close()
        }
    }

    @Test fun blockedSelectionPublishesItsLocalizedWarning() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any())).thenReturn(Result.failure(BlockedValidatorException))
            f.model.blockProducerClicked(f.row())
            assertEquals("Validator blocked", f.model.errorLiveData.value?.peekContent())
            verify(f.router, never()).back()
        } finally {
            f.close()
        }
    }

    @Test fun ordinarySelectionFailureCannotBeSilentlyDiscarded() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            whenever(f.producer.blockProducerSelected(any(), any(), any()))
                .thenReturn(Result.failure(IllegalStateException("Unable to update selection")))
            f.model.blockProducerClicked(f.row())
            assertEquals("Unable to update selection", f.model.errorLiveData.value?.peekContent())
            verify(f.router, never()).back()
        } finally {
            f.close()
        }
    }

    @Test fun validatorInfoCachesTheChosenMetadataBeforeNavigating() = runTest {
        val f = ProducerSearchScreenFixture()
        try {
            f.start()
            val validator = f.data.validators[1]
            val previous = mock<ValidatorDetailsParcelModel>()
            f.details.value = mapOf("unrelated-account" to previous)
            var cachedBeforeNavigation = false
            doAnswer {
                cachedBeforeNavigation = f.details.value[validator.accountIdHex]?.accountIdHex == validator.accountIdHex
                Unit
            }.whenever(f.router).openValidatorDetails(validator.accountIdHex)
            whenever(f.producer.navigateBlockProducerInfo(any(), any(), any(), any())).doSuspendableAnswer {
                it.getArgument<(Validator) -> Unit>(3)(validator)
            }
            f.model.blockProducerInfoClicked(f.row(1))
            verify(f.router).openValidatorDetails(validator.accountIdHex)
            assertTrue("Validator must be cached before navigation", cachedBeforeNavigation)
            assertSame(previous, f.details.value["unrelated-account"])
            val stored = f.details.value.getValue(validator.accountIdHex)
            assertEquals(validator.accountIdHex, stored.accountIdHex)
            assertEquals(validator.prefs?.blocked, stored.prefs?.isBlocked)
            assertEquals(BigDecimal("0.1"), stored.prefs?.commission)
            val stake = stored.stake as ValidatorStakeParcelModel.Active
            assertEquals(BigInteger("1200"), stake.totalStake)
            assertEquals(BigInteger("300"), stake.ownStake)
            assertEquals(BigDecimal("0.125"), stake.apy)
        } finally {
            f.close()
        }
    }

    @Test fun collatorInfoPreservesItsIdentityAndStakeMetadata() = runTest {
        val f = ProducerSearchScreenFixture(true)
        try {
            f.start()
            val collator = f.data.collators.getValue(f.addresses[0])
            whenever(f.producer.navigateBlockProducerInfo(any(), any(), any(), any())).doSuspendableAnswer {
                it.getArgument<(Collator) -> Unit>(2)(collator)
            }
            f.model.blockProducerInfoClicked(f.row())
            val details = argumentCaptor<CollatorDetailsParcelModel>()
            verify(f.router).openCollatorDetails(details.capture())
            assertEquals("0x" + "01".repeat(20), details.firstValue.accountIdHex)
            assertEquals(collator.totalCounted, details.firstValue.stake.totalStake)
            assertEquals(collator.identity?.display, details.firstValue.identity?.display)
            assertEquals(BigDecimal("12.5"), details.firstValue.stake.estimatedRewards)
            assertEquals(BigInteger("300"), details.firstValue.stake.selfBonded)
            assertEquals(4, details.firstValue.stake.delegations)
        } finally {
            f.close()
        }
    }
}
