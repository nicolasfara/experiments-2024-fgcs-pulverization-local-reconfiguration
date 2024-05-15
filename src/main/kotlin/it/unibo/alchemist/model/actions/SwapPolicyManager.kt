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
                if (toPercentage(smartphoneCapacityWhenSwap - smartphoneCapacity, smartphoneMaxCapacity) > 5.0) {
                    actuallyLocatedIn = Wearable
                    wearableCapacityWhenSwap = wearableCapacity!!
                    Wearable
                } else null
            }
            Wearable -> {
                if (toPercentage(wearableCapacityWhenSwap - wearableCapacity!!, wearableMaxCapacity) > 5.0) {
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

class SmartphoneSwapPolicyManager(
    private val smartphoneMaxCapacity: Double
) : SwapPolicyManager {
    private var actuallyLocatedIn: Component = Smartphone

    override fun manageSwap(smartphoneCapacity: Double, wearableCapacity: Double?): Component? {
        return if (toPercentage(smartphoneCapacity, smartphoneMaxCapacity) < 5.0 && wearableCapacity != null && actuallyLocatedIn == Smartphone) {
            actuallyLocatedIn = Wearable
            Wearable
        } else null
    }
}

class WearableSwapPolicyManager(
    private val wearableMaxCapacity: Double
) : SwapPolicyManager {
    private var actuallyLocatedIn: Component = Wearable

    override fun manageSwap(smartphoneCapacity: Double, wearableCapacity: Double?): Component? {
        return if (toPercentage(wearableCapacity!!, wearableMaxCapacity) < 5.0 && actuallyLocatedIn == Wearable) {
            actuallyLocatedIn = Smartphone
            Smartphone
        } else null
    }
}
