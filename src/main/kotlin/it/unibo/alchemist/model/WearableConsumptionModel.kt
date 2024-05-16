package it.unibo.alchemist.model

import org.apache.commons.math3.random.RandomGenerator

class WearableConsumptionModel<T> @JvmOverloads constructor(
    random: RandomGenerator,
    override val node: Node<T>,
    override val deviceEnergyPerInstruction: Double,
    osInstructions: Int,
    startingComponents: Iterable<Component> = emptySet(),
    val batteryCapacity: Double,
    private val rechargeRate: Double,
    ) : ConsumptionModel<T>(random, osInstructions, startingComponents.toSet()),
    PowerManager by PowerManagerImpl(random, batteryCapacity, batteryCapacity, rechargeRate)
{
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> {
        TODO("Not yet implemented")
    }
}
