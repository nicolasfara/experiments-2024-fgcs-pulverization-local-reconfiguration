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
    private val TraveledDistanceLastTenMinutes by molecule()
    private var lastPosition: P? = null
    private var traveledDistance = 0.0
    private val timeWindow = 600.0 // 10 minutes
    private var traveledDistancesSamples = mapOf<Double, Double>()

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val currentPosition = environment.getPosition(node)
        val currentTime = environment.simulation.time.toDouble()
        lastPosition?.let {
            val traveledDistanceSample = currentPosition.distanceTo(it)
            traveledDistance += traveledDistanceSample
            node.setConcentration(TraveledDistance, traveledDistance as T)
            traveledDistancesSamples += currentTime to traveledDistanceSample
            traveledDistancesSamples = traveledDistancesSamples.filter { s -> s.key > currentTime - timeWindow }
            node.setConcentration(TraveledDistanceLastTenMinutes, traveledDistancesSamples.values.sum() as T)
        }
        lastPosition = currentPosition
    }
}