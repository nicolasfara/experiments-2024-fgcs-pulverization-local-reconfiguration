package it.unibo.alchemist.model

sealed interface Component {
    val instructionNumber: Int
}
data class Behavior(override val instructionNumber: Int = 0) : Component
data class GpsSensor(override val instructionNumber: Int = 0) : Component
data class Communication(override val instructionNumber: Int = 0) : Component

abstract class ConsumptionModel<T> : NodeProperty<T> {
    private var activeComponents = emptySet<Component>()
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
     * Get the device consumption considering the delta from the last time update and the [currentTime].
     */
    fun getConsumptionSinceLastUpdate(currentTime: Double): Double {
        lastTimeUpdate = currentTime
        TODO()
    }
}
