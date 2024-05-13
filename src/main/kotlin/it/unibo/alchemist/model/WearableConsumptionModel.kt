package it.unibo.alchemist.model

import org.apache.commons.math3.random.RandomGenerator

class WearableConsumptionModel<T> @JvmOverloads constructor(
    random: RandomGenerator,
    override val node: Node<T>,
    override val deviceEnergyPerInstruction: Double,
    osInstructions: Int,
    startingComponents: Iterable<Component> = emptySet(),
) : ConsumptionModel<T>(random, osInstructions, startingComponents.toSet()) {
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> {
        TODO("Not yet implemented")
    }
}