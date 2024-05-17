package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.utils.molecule

class TraveledDistanceAction<T, P : Position<P>>(
    private val environment: Environment<T, P>,
    private val node: Node<T>,
) : AbstractLocalAction<T>(node) {
    private val TraveledDistance by molecule()
    private var lastPosition: P? = null
    private var traveledDistance = 0.0

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentPosition = environment.getPosition(node)
        lastPosition?.let {
            val traveledDistanceSample = currentPosition.distanceTo(it)
            traveledDistance += traveledDistanceSample
            node.setConcentration(TraveledDistance, traveledDistance as T)
        }
//        if (environment.simulation.time.toDouble().toInt() % 1000 == 0) {
//            println("Traveled distance at time ${environment.simulation.time.toDouble()}: $traveledDistance")
//        }
        lastPosition = currentPosition
    }
}