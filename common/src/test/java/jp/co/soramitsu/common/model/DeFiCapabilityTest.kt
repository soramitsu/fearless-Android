package jp.co.soramitsu.common.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeFiCapabilityTest {

    @Test
    fun `kill switch disables actions without hiding destination`() {
        val capability = DeFiCapability(
            featureId = "demeter",
            availability = DeFiAvailability.Available,
            accountRequirement = DeFiAccountRequirement.Sora,
            signingRequirement = DeFiSigningRequirement.SignableAccount,
            supportedNetworkIds = setOf("sora"),
            destinationEnabled = true,
            actionsEnabled = false,
            userFacingReason = "Farming actions are temporarily disabled."
        )

        assertTrue(capability.canOpenDestination)
        assertFalse(capability.canPerformAction)
    }

    @Test
    fun `disabled capability requires a user-facing reason`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeFiCapability(
                featureId = "pools",
                availability = DeFiAvailability.Available,
                accountRequirement = DeFiAccountRequirement.Sora,
                signingRequirement = DeFiSigningRequirement.SignableAccount,
                supportedNetworkIds = setOf("sora"),
                destinationEnabled = true,
                actionsEnabled = false
            )
        }
    }
}
