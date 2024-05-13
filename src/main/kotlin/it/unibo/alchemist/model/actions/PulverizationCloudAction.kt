package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.CloudConsumptionModel
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction

class PulverizationCloudAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>
) : AbstractLocalAction<T>(node) {
    private val cloudConsumptionModel: CloudConsumptionModel<*> by lazy {
        node.properties.filterIsInstance<CloudConsumptionModel<*>>().first()
    }

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }

    override fun execute() {

    }
}
