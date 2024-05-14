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
import it.unibo.alchemist.utils.toPercentage
import org.apache.commons.math3.random.RandomGenerator

class PulverizationAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>,
    private val minThreshold: Double,
    private val maxThreshold: Double,
    private val swapPolicy: String,
) : AbstractLocalAction<T>(node) {
    private val CloudInstance by molecule()
    private val MovementTarget by molecule()
    private val IsMoving by molecule()
    private val SmartphonePercentage by molecule()
    private val WearablePercentage by molecule()
    private val SmartphoneComponents by molecule()
    private val WearableComponents by molecule()

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
    private val policyManager: SwapPolicyManager by lazy {
        when (swapPolicy) {
            "smartphone" -> SmartphoneSwapPolicyManager(4500.0)
            "wearable" ->
                if (hasWearable) WearableSwapPolicyManager(306.0)
                else SmartphoneSwapPolicyManager(4500.0)
            "hybrid" ->
                if (hasWearable) HybridSwapPolicyManager(4500.0, 306.0)
                else SmartphoneSwapPolicyManager(4500.0)
            else -> error("Invalid swap policy: $swapPolicy")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentTime = environment.simulation.time.toDouble()
        initializeNode(swapPolicy)
        val smartphonePower = smartphoneConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
        smartphoneConsumptionModel.managePowerConsumption(currentTime, smartphonePower)

        wearableConsumptionModel?.let { wearableModel ->
            val wearablePower = wearableModel.getConsumptionSinceLastUpdate(currentTime)
            wearableModel.managePowerConsumption(currentTime, wearablePower)
        } ?: println("Node ${node.id} has no wearable, skipping wearable consumption management")

        val currentSmartphoneCapacity = smartphoneConsumptionModel.currentCapacity()
        val currentWearableCapacity = wearableConsumptionModel?.currentCapacity() ?: Double.POSITIVE_INFINITY
        val isCharging = smartphoneConsumptionModel.isCharging() || wearableConsumptionModel?.isCharging() ?: false

        node.setConcentration(
            SmartphonePercentage,
            toPercentage(currentSmartphoneCapacity, smartphoneConsumptionModel.batteryCapacity) as T
        )
        node.setConcentration(SmartphoneComponents, smartphoneConsumptionModel.getActiveComponents() as T)

        wearableConsumptionModel?.let {
            node.setConcentration(
                WearablePercentage,
                toPercentage(currentWearableCapacity, it.batteryCapacity) as T
            )
            node.setConcentration(WearableComponents, it.getActiveComponents() as T)
        }

        if (currentSmartphoneCapacity <= 0.0 || currentWearableCapacity <= 0.0 || isCharging) {
            println("Node ${node.id} has no battery left, stop moving ")
            node.setConcentration(MovementTarget, environment.getPosition(node) as T)
            node.setConcentration(IsMoving, false as T)
        }
        manageSwap()
    }

    private fun manageSwap() {
        val smartphoneCapacity = smartphoneConsumptionModel.currentCapacity()
        val wearableCapacity = wearableConsumptionModel?.currentCapacity()
        policyManager.manageSwap(smartphoneCapacity, wearableCapacity)?.let { component ->
            when (component) {
                Smartphone -> {
                    println("Swapping GPS from wearable to smartphone for node ${node.id}")
                    wearableConsumptionModel?.let { wearableModel ->
                        val gpsComponent = wearableModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                        wearableModel.removeActiveComponent(node.id, gpsComponent)
                        smartphoneConsumptionModel.setActiveComponent(node.id, gpsComponent)
                    } ?: error("No wearable present in node ${node.id}")
                }
                Wearable -> {
                    println("Swapping GPS from smartphone to wearable for node ${node.id}")
                    wearableConsumptionModel?.let { wearableModel ->
                        val gpsComponent = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                        smartphoneConsumptionModel.removeActiveComponent(node.id, gpsComponent)
                        wearableModel.setActiveComponent(node.id, gpsComponent)
                    } ?: error("No wearable present in node ${node.id}")
                }
            }
        }
    }

    private fun initializeNode(scenario: String) {
        if (!isFirstExecution) {
            smartphoneConsumptionModel.initializeCapacityRandomly()
            wearableConsumptionModel?.initializeCapacityRandomly()
            setupGpsAllocation(scenario)
            isFirstExecution = true
        }
    }

    private fun setupGpsAllocation(scenario: String) {
        when (scenario) {
            "smartphone" -> Unit
            "wearable", "hybrid" -> {
                wearableConsumptionModel?.let { wearableModel ->
                    println("Node ${node.id} has a wearable, moving GPS to wearable")
                    val gpsComponent = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                    smartphoneConsumptionModel.removeActiveComponent(node.id, gpsComponent)
                    wearableModel.setActiveComponent(node.id, gpsComponent)
                } ?: println("Node ${node.id} has no wearable, GPS will remain in smartphone")
            }
            else -> error("Invalid scenario: $scenario")
        }
    }

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }
}
