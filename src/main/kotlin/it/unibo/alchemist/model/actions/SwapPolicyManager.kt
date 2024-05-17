package it.unibo.alchemist.model.actions

import it.unibo.alchemist.utils.toPercentage

sealed interface Component
data object Smartphone : Component
data object Wearable : Component

interface SwapPolicyManager {
    fun manageSwap(smartphoneCapacity: Double, wearableCapacity: Double?): Component?
}

class HybridSwapPolicyManager(
    private val smartphoneMaxCapacity: Double,
    private val wearableMaxCapacity: Double,
) : SwapPolicyManager {
    private var actuallyLocatedIn: Component = Wearable
    private var smartphoneCapacityWhenSwap = smartphoneMaxCapacity
    private var wearableCapacityWhenSwap = wearableMaxCapacity

    override fun manageSwap(smartphoneCapacity: Double, wearableCapacity: Double?): Component? {
        return when (actuallyLocatedIn) {
            Smartphone -> {
                if (toPercentage(smartphoneCapacityWhenSwap - smartphoneCapacity, smartphoneMaxCapacity) > 5.0 && wearableCapacity!! > smartphoneCapacity) {
                    actuallyLocatedIn = Wearable
                    wearableCapacityWhenSwap = wearableCapacity
                    Wearable
                } else null
            }
            Wearable -> {
                if (toPercentage(wearableCapacityWhenSwap - wearableCapacity!!, wearableMaxCapacity) > 5.0 && smartphoneCapacity > wearableCapacity) {
                    actuallyLocatedIn = Smartphone
                    smartphoneCapacityWhenSwap = smartphoneCapacity
                    Smartphone
                } else null
            }
        }
    }
}

class DoNotSwapPolicyManager : SwapPolicyManager {
    override fun manageSwap(smartphoneCapacity: Double, wearableCapacity: Double?): Component? = null
}
