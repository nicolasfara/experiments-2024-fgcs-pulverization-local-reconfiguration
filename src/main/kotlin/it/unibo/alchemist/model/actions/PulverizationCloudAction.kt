package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.CloudConsumptionModel
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.utils.molecule
import kotlin.math.ceil
import kotlin.math.floor

class PulverizationCloudAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>,
    private val cloudCost: Double,
) : AbstractLocalAction<T>(node) {
    private val CloudActiveComponents by molecule()
    private val CloudComponentsTime by molecule()
    private val CloudPower by molecule()
    private val CloudCost by molecule()
    private val CloudInstance by molecule()

    private val cloudConsumptionModel: CloudConsumptionModel<*> by lazy {
        node.properties.filterIsInstance<CloudConsumptionModel<*>>().first()
    }
    private var componentsExecutionTime = 0.0
    private var lastUpdate = 0.0
    private var actualCloudCost = 0.0

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentTime = environment.simulation.time.toDouble()
        val activeComponents = cloudConsumptionModel.getActiveComponents()
        node.setConcentration(CloudActiveComponents, activeComponents as T)
        val delta = currentTime - lastUpdate

        val cloudConsumption = cloudConsumptionModel.getConsumptionSinceLastUpdate(currentTime)
        node.setConcentration(CloudPower, cloudConsumption as T)

        val cloudInstances = ceil(cloudConsumption / 220.0) // 220W TDP per cloud instance
        node.setConcentration(CloudInstance, cloudInstances as T)

        val instantCost = (cloudCost / 3600) * delta * cloudInstances
        actualCloudCost += instantCost
        node.setConcentration(CloudCost, actualCloudCost as T)

        componentsExecutionTime += delta * cloudConsumptionModel.getActiveComponents().count()
        node.setConcentration(CloudComponentsTime, componentsExecutionTime as T)
        lastUpdate = currentTime
    }
}
