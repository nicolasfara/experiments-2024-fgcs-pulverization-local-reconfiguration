package it.unibo.alchemist.model

import org.apache.commons.math3.random.RandomGenerator

sealed interface Component {
    val instructionNumber: Int
}
data class Behavior(override val instructionNumber: Int = 0) : Component
data class GpsSensor(override val instructionNumber: Int = 0) : Component
data class Communication(override val instructionNumber: Int = 0) : Component

abstract class ConsumptionModel<T>(
    private val random: RandomGenerator,
    private val osInstructions: Int,
    startingComponents: Set<Component>
) : NodeProperty<T> {
    private var activeComponents = startingComponents
    private var lastTimeUpdate = 0.0

    /**
     * The energy consumed by the device per instruction.
     */
    abstract val deviceEnergyPerInstruction: Double

    /**
     * Specify that the given [component] is executed by this [node].
     */
    fun setActiveComponent(component: Component) {
        activeComponents += component
    }

    /**
     * Specify that the given [component] is no longer executed by this [node].
     */
    fun removeActiveComponent(component: Component) {
        activeComponents -= component
    }

    /**
     * Get the device consumption considering the delta from the last time update and the [currentTime].
     * Returns the consumption in Watts/h.
     */
    fun getConsumptionSinceLastUpdate(currentTime: Double): Double {
        val executedInstructions = activeComponents.sumOf { it.instructionNumber } + (osInstructions * random.nextDouble())
        val delta = currentTime - lastTimeUpdate // in seconds
        val consumedEnergy = executedInstructions * deviceEnergyPerInstruction // in Joules
        lastTimeUpdate = currentTime
        return when {
            delta == 0.0 -> 0.0
            else -> consumedEnergy / (delta * 3600)
        }
    }
}
