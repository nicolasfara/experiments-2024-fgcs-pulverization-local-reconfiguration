package it.unibo.alchemist.it.unibo.alchemist.boundary.extractors

import it.unibo.alchemist.boundary.extractors.AbstractDoubleExporter
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.WearableConsumptionModel

class SensorOffloadedInWearable : AbstractDoubleExporter() {
    override val columnNames: List<String> = listOf("PercentageSensorInWearable")

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long
    ): Map<String, Double> {
        val totalWearables = environment.nodes
            .filter { it.properties.filterIsInstance<WearableConsumptionModel<*>>().isNotEmpty() }
            .size
        val wearableHostingSensor = environment.nodes
            .filter {
                val wearable = it.properties.filterIsInstance<WearableConsumptionModel<*>>().firstOrNull()
                wearable?.getActiveComponents()?.isNotEmpty() ?: false
            }
            .size
        return mapOf("PercentageSensorInWearable" to (wearableHostingSensor.toDouble() / totalWearables) * 100.0)
    }
}