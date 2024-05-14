package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Behavior
import it.unibo.alchemist.model.ConsumptionModel
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.utils.molecule
import it.unibo.alchemist.utils.toPercentage
import org.apache.commons.math3.random.RandomGenerator

class PowerManager<T>(
    private val random: RandomGenerator,
    private val node: Node<T>,
    private val cloudConsumptionModel: ConsumptionModel<*>,
    private val smartphoneConsumptionModel: ConsumptionModel<*>,
    private val wearableConsumptionModel: ConsumptionModel<*>?,
    private val minThreshold: Double,
    private val maxThreshold: Double,
    private val hasWearable: Boolean
) {
    private val SmartphoneBatteryCapacity by molecule()
    private val WearableBatteryCapacity by molecule()
    private val SmartphonePercentage by molecule()
    private val WearablePercentage by molecule()
    private val ComponentsInSmartphone by molecule()
    private val ComponentsInWearables by molecule()

    @Suppress("UNCHECKED_CAST")
    fun managePowerConsumption(currentTime: Double) {
        if (toPercentage(currentSmartphoneCapacity(), 4500.0) < minThreshold) {
            val behavior = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<Behavior>().firstOrNull()
            behavior?.let {
                smartphoneConsumptionModel.removeActiveComponent(node.id, it)
                cloudConsumptionModel.setActiveComponent(node.id, it)
            } ?: println("Behavior component already swapped to cloud for node ${node.id}")
        }
        if (currentSmartphoneCapacity() > 0.0) {
            val smartphoneConsumption =
                smartphoneConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
            val smartphoneNewCapacity = currentSmartphoneCapacity() - toMilliAmpsPerHour(smartphoneConsumption)
            node.setConcentration(SmartphoneBatteryCapacity, (if (smartphoneNewCapacity < 0.0) 0.0 else smartphoneNewCapacity) as T)
            node.setConcentration(SmartphonePercentage, (if (smartphoneNewCapacity < 0.0) 0.0 else (smartphoneNewCapacity / 4500.0)) as T)
        }
        if (hasWearable && currentWearableCapacity()!! > 0.0) {
            val wearableConsumption =
                wearableConsumptionModel?.getConsumptionSinceLastUpdate(currentTime) ?: 0.0
            val wearableNewCapacity = currentWearableCapacity()!! - toMilliAmpsPerHour(wearableConsumption)
            node.setConcentration(WearableBatteryCapacity, (if (wearableNewCapacity < 0.0) 0.0 else wearableNewCapacity) as T)
            node.setConcentration(WearablePercentage, (if (wearableNewCapacity < 0.0) 0.0 else (wearableNewCapacity / 306.0)) as T)

            node.setConcentration(ComponentsInWearables, wearableConsumptionModel?.getActiveComponents() as T)
        }
        node.setConcentration(ComponentsInSmartphone, smartphoneConsumptionModel.getActiveComponents() as T)
    }

    @Suppress("UNCHECKED_CAST")
    fun initializeBatteryCapacities() {
        val initSmartphoneCapacity = randomInitializeBatteryCapacity(4500.0)
        node.setConcentration(SmartphoneBatteryCapacity, initSmartphoneCapacity as T)
        if (hasWearable) {
            val initWearableCapacity = randomInitializeBatteryCapacity(306.0)
            node.setConcentration(WearableBatteryCapacity, initWearableCapacity as T)
        }
    }

    fun getSmartphoneBatteryCapacity(): Double = currentSmartphoneCapacity()
    fun getWearableBatteryCapacity(): Double? = currentWearableCapacity()

    private fun randomInitializeBatteryCapacity(maxCapacity: Double): Double {
        val max = maxThreshold / 100.0 * maxCapacity
        val min = minThreshold / 100.0 * maxCapacity
        return maxCapacity - (random.nextDouble() * (max - min))
    }
    private fun currentSmartphoneCapacity(): Double = node.getConcentration(SmartphoneBatteryCapacity) as Double
    private fun currentWearableCapacity(): Double? = node.getConcentration(WearableBatteryCapacity) as Double?

    private fun toMilliAmpsPerHour(wattsHour: Double): Double = wattsHour / 3.3 * 1E3
}