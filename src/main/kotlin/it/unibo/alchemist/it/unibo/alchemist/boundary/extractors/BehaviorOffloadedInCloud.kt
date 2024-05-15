package it.unibo.alchemist.it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.boundary.extractors.AbstractDoubleExporter
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Behavior
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.SmartphoneConsumptionModel
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.WearableConsumptionModel

class BehaviorOffloadedInCloud : AbstractDoubleExporter() {
    override val columnNames: List<String> = listOf("BehaviorOffloadedInCloud")

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long
    ): Map<String, Double> {
        val totalBehavior = environment.nodes
            .filter { it.properties.filterIsInstance<SmartphoneConsumptionModel<*>>().isNotEmpty() }
            .size
        val behaviorInCloud = environment.nodes
            .filter {
                val smartphone = it.properties.filterIsInstance<SmartphoneConsumptionModel<*>>().firstOrNull()
                smartphone?.getActiveComponents()?.filterIsInstance<Behavior>()?.isEmpty() ?: false
            }
            .size
        return mapOf("BehaviorOffloadedInCloud" to (behaviorInCloud.toDouble() / totalBehavior) * 100.0)
    }
}
