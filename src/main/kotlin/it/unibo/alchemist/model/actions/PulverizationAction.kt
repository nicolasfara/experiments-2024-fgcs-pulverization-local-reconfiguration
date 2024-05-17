package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Behavior
import it.unibo.alchemist.model.CloudConsumptionModel
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GpsSensor
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.SmartphoneConsumptionModel
import it.unibo.alchemist.model.WearableConsumptionModel
import it.unibo.alchemist.utils.molecule
import it.unibo.alchemist.utils.toPercentage

class PulverizationAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>,
    private val thresholds: Iterable<Double>,
    private val swapPolicy: String,
    private val smartphoneCapacity: Double,
    private val wearableCapacity: Double,
) : AbstractLocalAction<T>(node) {
    private val CloudInstance by molecule()
    private val IsMoving by molecule()
    private val SmartphonePercentage by molecule()
    private val WearablePercentage by molecule()
    private val SmartphoneComponents by molecule()
    private val WearableComponents by molecule()
    private val SmartphoneCurrentCapacity by molecule()
    private val WearableCurrentCapacity by molecule()
    private val SmartphoneCharging by molecule()
    private val WearableCharging by molecule()
    private val SmartphoneComponentsTime by molecule()
    private val WearableComponentsTime by molecule()
    private val SmartphonePower by molecule()
    private val WearablePower by molecule()

    private val minThreshold by lazy {
        require(thresholds.count() == 2) { "Thresholds must contain exactly two values" }
        thresholds.first()
    }
    private val maxThreshold by lazy {
        require(thresholds.count() == 2) { "Thresholds must contain exactly two values" }
        thresholds.last()
    }

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
            "smartphone", "wearable", "none" -> DoNotSwapPolicyManager()
            "hybrid" ->
                if (hasWearable) HybridSwapPolicyManager(smartphoneCapacity, wearableCapacity)
                else DoNotSwapPolicyManager()
            else -> error("Invalid swap policy: $swapPolicy")
        }
    }
    private var smartphoneComponentsExecutionTime = 0.0
    private var wearableComponentsExecutionTime = 0.0
    private var lastRead = 0.0

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentTime = environment.simulation.time.toDouble()
        val delta = currentTime - lastRead
        initializeNode(swapPolicy)

        val isSmartphoneCharging = smartphoneConsumptionModel.isCharging()
        val isWearableCharging = wearableConsumptionModel?.isCharging() ?: false
        val isCharging = isSmartphoneCharging || isWearableCharging
        // Recharge if needed
        if (!isSmartphoneCharging) {
            val smartphonePower = smartphoneConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
            smartphoneConsumptionModel.managePowerConsumption(currentTime, smartphonePower)
            node.setConcentration(SmartphoneCharging, false as T)
            node.setConcentration(SmartphonePower, (smartphonePower * delta) as T)
        } else {
            smartphoneConsumptionModel.rechargeStep(currentTime)
            node.setConcentration(SmartphoneCharging, true as T)
        }
        if (!isWearableCharging) {
            wearableConsumptionModel?.let { wearableModel ->
                val wearablePower = wearableModel.getConsumptionSinceLastUpdate(currentTime)
                wearableModel.managePowerConsumption(currentTime, wearablePower)
                node.setConcentration(WearableCharging, false as T)
                node.setConcentration(WearablePower, (wearablePower * delta) as T)
            } // ?: println("Node ${node.id} has no wearable, skipping wearable consumption management")
        } else {
            wearableConsumptionModel?.rechargeStep(currentTime)
            node.setConcentration(WearableCharging, true as T)
        }
        // Write smartphone battery status
        val currentSmartphoneCapacity = smartphoneConsumptionModel.currentCapacity()
        node.setConcentration(
            SmartphonePercentage,
            toPercentage(currentSmartphoneCapacity, smartphoneConsumptionModel.batteryCapacity) as T
        )
        node.setConcentration(SmartphoneCurrentCapacity, currentSmartphoneCapacity as T)
        node.setConcentration(SmartphoneComponents, smartphoneConsumptionModel.getActiveComponents() as T)
        // Write wearable battery status if present
        val currentWearableCapacity = wearableConsumptionModel?.currentCapacity() ?: Double.POSITIVE_INFINITY
        wearableConsumptionModel?.let {
            node.setConcentration(
                WearablePercentage,
                toPercentage(currentWearableCapacity, it.batteryCapacity) as T
            )
            node.setConcentration(WearableComponents, it.getActiveComponents() as T)
            node.setConcentration(WearableCurrentCapacity, currentWearableCapacity as T)
        }
        // Stop moving if no battery left
        if (currentSmartphoneCapacity <= 0.0 || currentWearableCapacity <= 0.0 || isCharging) {
            // println("Node ${node.id} has no battery left, stop moving ")
            node.setConcentration(IsMoving, false as T)
        } else {
            node.setConcentration(IsMoving, true as T)
        }
        manageSwapBetweenSmartphoneAndWearable()
        // Write components execution time
        smartphoneComponentsExecutionTime += delta * smartphoneConsumptionModel.getActiveComponents().count()
        wearableComponentsExecutionTime += delta * (wearableConsumptionModel?.getActiveComponents()?.count() ?: 0)
        node.setConcentration(SmartphoneComponentsTime, smartphoneComponentsExecutionTime as T)
        node.setConcentration(WearableComponentsTime, wearableComponentsExecutionTime as T)
        lastRead = currentTime
    }

    private fun manageSwapBetweenSmartphoneAndWearable() {
        manageBehaviorAllocation()
        val smartphoneCapacity = smartphoneConsumptionModel.currentCapacity()
        val wearableCapacity = wearableConsumptionModel?.currentCapacity()
        policyManager.manageSwap(smartphoneCapacity, wearableCapacity)?.let { component ->
            when (component) {
                Smartphone -> {
                    // println("Swapping GPS from wearable to smartphone for node ${node.id}")
                    wearableConsumptionModel?.let { wearableModel ->
                        val gpsComponent = wearableModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                        wearableModel.removeActiveComponent(node.id, gpsComponent)
                        smartphoneConsumptionModel.setActiveComponent(node.id, gpsComponent)
                    } ?: error("No wearable present in node ${node.id}")
                }
                Wearable -> {
                    // println("Swapping GPS from smartphone to wearable for node ${node.id}")
                    wearableConsumptionModel?.let { wearableModel ->
                        val gpsComponent = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                        smartphoneConsumptionModel.removeActiveComponent(node.id, gpsComponent)
                        wearableModel.setActiveComponent(node.id, gpsComponent)
                    } ?: error("No wearable present in node ${node.id}")
                }
            }
        }
    }

    private fun manageBehaviorAllocation() {
        val smartphoneCapacity = smartphoneConsumptionModel.currentCapacity()
        val smartphonePercentage = toPercentage(smartphoneCapacity, smartphoneConsumptionModel.batteryCapacity)
        val isCharging = smartphoneConsumptionModel.isCharging()
        if (smartphonePercentage < minThreshold && !isCharging) {
            val behavior = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<Behavior>().firstOrNull()
            behavior?.let {
                // println("Node ${node.id} has a behavior in smartphone, moving it to cloud")
                smartphoneConsumptionModel.removeActiveComponent(node.id, it)
                cloudConsumptionModel.setActiveComponent(node.id, it)
            } // ?: println("Node ${node.id} has no behavior in smartphone, skipping behavior allocation")
        }
        if (isCharging && (minThreshold != 0.0 && minThreshold != 100.0)) {
            val behavior = cloudConsumptionModel.getActiveComponents().filterIsInstance<Behavior>().firstOrNull()
            behavior?.let {
                // println("Node ${node.id} has a behavior in cloud, moving it to smartphone")
                cloudConsumptionModel.removeActiveComponent(node.id, it)
                smartphoneConsumptionModel.setActiveComponent(node.id, it)
            } // ?: println("Node ${node.id} has no behavior in cloud, skipping behavior allocation")
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
            "smartphone", "none" -> Unit
            "wearable", "hybrid" -> {
                wearableConsumptionModel?.let { wearableModel ->
                    // println("Node ${node.id} has a wearable, moving GPS to wearable")
                    val gpsComponent = smartphoneConsumptionModel.getActiveComponents().filterIsInstance<GpsSensor>().first()
                    smartphoneConsumptionModel.removeActiveComponent(node.id, gpsComponent)
                    wearableModel.setActiveComponent(node.id, gpsComponent)
                } // ?: println("Node ${node.id} has no wearable, GPS will remain in smartphone")
            }
            else -> error("Invalid scenario: $scenario")
        }
    }

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }
}
