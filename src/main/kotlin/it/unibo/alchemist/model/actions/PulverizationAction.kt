package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.CloudConsumptionModel
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GpsSensor
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.SmartphoneConsumptionModel
import it.unibo.alchemist.model.WearableConsumptionModel
import it.unibo.alchemist.utils.molecule
import org.apache.commons.math3.random.RandomGenerator

class PulverizationAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>,
    private val random: RandomGenerator,
    private val minThreshold: Double,
    private val maxThreshold: Double,
    private val swapPolicy: String,
) : AbstractLocalAction<T>(node) {
    private val SmartphoneBatteryCapacity by molecule()
    private val WearableBatteryCapacity by molecule()
    private val SmartphonePercentage by molecule()
    private val WearablePercentage by molecule()
    private val CloudInstance by molecule()
    private val ComponentsInSmartphone by molecule()
    private val ComponentsInWearables by molecule()
    private val cloudConsumptionModel: CloudConsumptionModel<*> by lazy {
        environment.nodes.first { it.contains(CloudInstance) }
            .properties.filterIsInstance<CloudConsumptionModel<*>>().first()
    }
    private val smartphoneConsumptionModel: SmartphoneConsumptionModel<*> by lazy {
        node.properties.filterIsInstance<SmartphoneConsumptionModel<*>>().first()
    }
    private val wearableConsumptionModel: WearableConsumptionModel<*>? by lazy {
        node.properties.filterIsInstance<WearableConsumptionModel<*>>().firstOrNull()
    }
    private val hasWearable by lazy { wearableConsumptionModel != null }
    private var isFirstExecution = false

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentTime = environment.simulation.time.toDouble()
        initializeNode()
        if (currentSmartphoneCapacity() > 0.0) {
            val smartphoneConsumption =
                smartphoneConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
            val smartphoneNewCapacity = currentSmartphoneCapacity() - toMilliAmpsPerHour(smartphoneConsumption)
            node.setConcentration(SmartphoneBatteryCapacity, (if (smartphoneNewCapacity < 0.0) 0.0 else smartphoneNewCapacity) as T)
            node.setConcentration(SmartphonePercentage, (if (smartphoneNewCapacity < 0.0) 0.0 else (smartphoneNewCapacity / 4500.0)) as T)
        }
        if (hasWearable && currentWearableCapacity() > 0.0) {
            val wearableConsumption =
                wearableConsumptionModel?.getConsumptionSinceLastUpdate(currentTime) ?: 0.0
            val wearableNewCapacity = currentWearableCapacity() - toMilliAmpsPerHour(wearableConsumption)
            node.setConcentration(WearableBatteryCapacity, (if (wearableNewCapacity < 0.0) 0.0 else wearableNewCapacity) as T)
            node.setConcentration(WearablePercentage, (if (wearableNewCapacity < 0.0) 0.0 else (wearableNewCapacity / 306.0)) as T)

            node.setConcentration(ComponentsInWearables, wearableConsumptionModel?.getActiveComponents() as T)
        }
        node.setConcentration(ComponentsInSmartphone, smartphoneConsumptionModel.getActiveComponents() as T)
    }

    @Suppress("UNCHECKED_CAST")
    private fun initializeNode() {
        if (!isFirstExecution) {
            val initSmartphoneCapacity = randomInitializeBatteryCapacity(4500.0)
            node.setConcentration(SmartphoneBatteryCapacity, initSmartphoneCapacity as T)
            if (hasWearable) {
                setupGpsAllocation()
                val initWearableCapacity = randomInitializeBatteryCapacity(306.0)
                node.setConcentration(WearableBatteryCapacity, initWearableCapacity as T)
            }
            isFirstExecution = true
        }
    }

    private fun randomInitializeBatteryCapacity(maxCapacity: Double): Double {
        val max = maxThreshold / 100.0 * maxCapacity
        val min = minThreshold / 100.0 * maxCapacity
        return maxCapacity - (random.nextDouble() * (max - min))
    }

    private fun setupGpsAllocation() {
        val gpsComponent = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
        smartphoneConsumptionModel.removeActiveComponent(gpsComponent)
        wearableConsumptionModel?.setActiveComponent(gpsComponent)
    }

    private fun currentSmartphoneCapacity(): Double = node.getConcentration(SmartphoneBatteryCapacity) as Double
    private fun currentWearableCapacity(): Double = node.getConcentration(WearableBatteryCapacity) as Double

    private fun toMilliAmpsPerHour(wattsHour: Double): Double = wattsHour / 3.3 * 1E3

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }
}
