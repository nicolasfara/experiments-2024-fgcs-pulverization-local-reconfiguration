package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.utils.molecule
import org.apache.commons.math3.random.RandomGenerator

class ConfigureNextPoi<T, P : Position<P>>(
    private val environment: Environment<T, P>,
    private val random: RandomGenerator,
    private val node: Node<T>,
    private val maxTimeInPoi: Double,
) : AbstractLocalAction<T>(node) {
    private val PoI by molecule()
    private val MovementTarget by molecule()
    private val availablePoi by lazy { environment.nodes.filter { it.contains(PoI) }.map { environment.getPosition(it) } }

    private var currentPoiPosition: P? = null
    private var timeInPoi = nextTimeInPoi()
    private var timeInPoiCounter = 0.0

    override fun cloneAction(node: Node<T>?, reaction: Reaction<T>?): Action<T> = TODO("Not yet implemented")

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        when {
            currentPoiPosition == null -> {
                currentPoiPosition = if (random.nextBoolean()) nearestPoi() else randomPoi()
                timeInPoi = nextTimeInPoi()
                timeInPoiCounter = simulationTime()
            }
            nodePosition().distanceTo(currentPoiPosition!!) < 5.0 && simulationTime() - timeInPoiCounter >= timeInPoi -> {
                currentPoiPosition = if (random.nextBoolean()) nearestPoi() else randomPoi()
                timeInPoi = nextTimeInPoi()
                timeInPoiCounter = simulationTime()
            }
            else -> Unit
        }
        node.setConcentration(MovementTarget, currentPoiPosition as T)
    }

    private fun nodePosition(): P = environment.getPosition(node)
    private fun simulationTime(): Double = environment.simulation.time.toDouble()
    private fun nextTimeInPoi(): Double = maxTimeInPoi * random.nextDouble()

    private fun nearestPoi(): P {
        return availablePoi
            .filter { it != currentPoiPosition }
            .map { it to it.distanceTo(environment.getPosition(node)) }
            .minBy { it.second }
            .first
    }

    private fun randomPoi(): P {
        val randomIndex = random.nextInt(availablePoi.size - 2)
        return availablePoi.filter { it != currentPoiPosition }[randomIndex]
    }
}