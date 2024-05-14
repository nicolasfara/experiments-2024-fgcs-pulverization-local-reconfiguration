package it.unibo.alchemist.model

import org.apache.commons.math3.random.RandomGenerator

class SmartphoneConsumptionModel<T>(
    random: RandomGenerator,
    override val node: Node<T>,
    override val deviceEnergyPerInstruction: Double,
    osInstructions: Int,
    startingComponents: Iterable<Component>,
    val batteryCapacity: Double,
) : ConsumptionModel<T>(random, osInstructions, startingComponents.toSet()),
    PowerManager by PowerManagerImpl(random, batteryCapacity, batteryCapacity)
{
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> {
        TODO("Not yet implemented")
    }
}