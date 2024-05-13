package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.SmartphoneConsumptionModel
import it.unibo.alchemist.model.WearableConsumptionModel
import it.unibo.alchemist.utils.molecule

class PulverizationAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>,
    private val minThreshold: Double,
    private val maxThreshold: Double,
) : AbstractLocalAction<T>(node) {
    private val SmartphoneBatteryCapacity by molecule()
    private val WearableBatteryCapacity by molecule()
    private val SmartphonePercentage by molecule()
    private val WearablePercentage by molecule()
    private val smartphoneConsumptionModel: SmartphoneConsumptionModel<*> by lazy {
        node.properties.filterIsInstance<SmartphoneConsumptionModel<*>>().first()
    }
    private val wearableConsumptionModel: WearableConsumptionModel<*>? by lazy {
        node.properties.filterIsInstance<WearableConsumptionModel<*>>().firstOrNull()
    }
    private val hasWearable by lazy { wearableConsumptionModel != null }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentTime = environment.simulation.time.toDouble()
        val smartphoneConsumption =
            smartphoneConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
        val smartphoneNewCapacity = currentSmartphoneCapacity() - toMilliAmpsPerHour(smartphoneConsumption)
        node.setConcentration(SmartphoneBatteryCapacity, smartphoneNewCapacity as T)
        node.setConcentration(SmartphonePercentage, (smartphoneNewCapacity / 4500.0) as T)
        if (hasWearable) {
            val wearableConsumption =
                wearableConsumptionModel?.getConsumptionSinceLastUpdate(currentTime) ?: 0.0
            val wearableNewCapacity = currentWearableCapacity() - toMilliAmpsPerHour(wearableConsumption)
            node.setConcentration(WearableBatteryCapacity, wearableNewCapacity as T)
            node.setConcentration(WearablePercentage, (wearableNewCapacity / 306.0) as T)
        }
    }

    private fun currentSmartphoneCapacity(): Double = node.getConcentration(SmartphoneBatteryCapacity) as Double
    private fun currentWearableCapacity(): Double = node.getConcentration(WearableBatteryCapacity) as Double

    private fun toMilliAmpsPerHour(wattsHour: Double): Double = wattsHour / 3.3 * 1E3

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }
}
