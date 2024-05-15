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
    private val activeComponents: MutableMap<Int, Set<Component>> by lazy { mutableMapOf(node.id to startingComponents) }
    private var lastTimeUpdate = 0.0

    /**
     * The energy consumed by the device per instruction.
     */
    abstract val deviceEnergyPerInstruction: Double

    /**
     * Get the active components executed by this [node].
     */
    fun getActiveComponents(): List<Component> = activeComponents.values.flatten()

    /**
     * Specify that the given [component] is executed by this [node].
     */
    fun setActiveComponent(id: Int, component: Component) {
        activeComponents[id] = activeComponents.getOrDefault(id, emptySet()) + component
    }

    /**
     * Specify that the given [component] is no longer executed by this [node].
     */
    fun removeActiveComponent(id: Int, component: Component) {
        activeComponents[id] = activeComponents.getOrDefault(id, emptySet()) - component
    }

    /**
     * Get the device consumption considering the delta from the last time update and the [currentTime].
     * Returns the consumption in Watts/h.
     */
    fun getConsumptionSinceLastUpdate(currentTime: Double): Double {
        val _executedInstructions = activeComponents.values.flatten().sumOf { it.instructionNumber / 1e9 } +
                osInstructions / 1e9 * random.nextDouble()
        val executedInstructions = _executedInstructions * 1e9
        val delta = currentTime - lastTimeUpdate // in seconds
        val consumedEnergy = executedInstructions * deviceEnergyPerInstruction // in Joules
        lastTimeUpdate = currentTime
        return when {
            delta == 0.0 -> 0.0
            else -> consumedEnergy / (delta * 3600)
        }
    }
}
