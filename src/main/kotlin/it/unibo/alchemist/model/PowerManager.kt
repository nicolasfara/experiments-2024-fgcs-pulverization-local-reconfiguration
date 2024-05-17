package it.unibo.alchemist.model

import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.random.RandomGenerator

interface PowerManager {
    fun managePowerConsumption(currentTime: Double, consumptionWattHours: Double): Double
    fun rechargeStep(currentTime: Double): Double
    fun initializeCapacityRandomly(): Double
    fun currentCapacity(): Double
    fun isCharging(): Boolean
}

class PowerManagerImpl(
    private val random: RandomGenerator,
    initialBatteryCapacity: Double,
    private val maxCapacity: Double,
    private val averageRechargeTime: Double,
) : PowerManager {
    private var currentCapacity = initialBatteryCapacity
    private var isCharging = random.nextDouble() < 0.9
    private var lastTimeUpdate = 0.0
    private val negativeExponential = ExponentialDistribution(random, 600.0)
    private var chargingConditionDelta = negativeExponential.sample()

    override fun managePowerConsumption(currentTime: Double, consumptionWattHours: Double): Double {
        val consumptionToMah = toMilliAmpsPerHour(consumptionWattHours) / 3600.0 // get mAh consumed in 1 second
        val newCapacity = currentCapacity - consumptionToMah
        if (newCapacity < 0.0) {
            isCharging = true
            lastTimeUpdate = currentTime
        }
        currentCapacity = if (newCapacity < 0.0) 0.0 else newCapacity
        return currentCapacity
    }

    override fun rechargeStep(currentTime: Double): Double {
        val delta = 1 // to be fix for the real time
        val newCapacity = currentCapacity + delta * ((maxCapacity / averageRechargeTime) / (3600 + chargingConditionDelta)) // 3000 mAh recharge rate
        currentCapacity = newCapacity
        if (newCapacity >= maxCapacity) {
            isCharging = false
            currentCapacity = maxCapacity
            chargingConditionDelta = negativeExponential.sample()
        }
        return newCapacity
    }

    override fun initializeCapacityRandomly(): Double {
        val initCapacity = randomInitializeBatteryCapacity(maxCapacity)
        currentCapacity = initCapacity
        return initCapacity
    }

    override fun currentCapacity(): Double = currentCapacity
    override fun isCharging(): Boolean = isCharging

    private fun randomInitializeBatteryCapacity(maxCapacity: Double): Double {
        val sixtyPercent = 0.6 * maxCapacity
        val delta = maxCapacity - sixtyPercent
        return sixtyPercent + delta * random.nextDouble()
    }

    private fun toMilliAmpsPerHour(wattsHour: Double): Double = wattsHour * 1000 / 3.3
}
