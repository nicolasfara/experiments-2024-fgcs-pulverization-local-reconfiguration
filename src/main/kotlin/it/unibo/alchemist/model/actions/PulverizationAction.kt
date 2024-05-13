package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.SmartphoneConsumptionModel
import it.unibo.alchemist.model.WearableConsumptionModel

class PulverizationAction<T>(
    private val environment: Environment<T, *>,
    private val node: Node<T>
) : AbstractLocalAction<T>(node) {

    private val smartphoneConsumptionModel: SmartphoneConsumptionModel<*> by lazy {
        node.properties.filterIsInstance<SmartphoneConsumptionModel<*>>().first()
    }
    private val wearableConsumptionModel: WearableConsumptionModel<*>? by lazy {
        node.properties.filterIsInstance<WearableConsumptionModel<*>>().firstOrNull()
    }

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }

    override fun execute() {

    }
}
