package it.unibo.alchemist.model

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
) : PowerManager {
    private var currentCapacity = initialBatteryCapacity
    private var isCharging = false
    private var lastTimeUpdate = 0.0

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
        val delta = currentTime - lastTimeUpdate
        val newCapacity = currentCapacity + delta * (3000.0 / 3600) // 3000 mAh recharge rate
        if (newCapacity >= maxCapacity) {
            isCharging = false
            currentCapacity = maxCapacity
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
//        val max = maxThreshold / 100.0 * maxCapacity
//        val min = minThreshold / 100.0 * maxCapacity
        return maxCapacity * random.nextDouble()
    }

    private fun toMilliAmpsPerHour(wattsHour: Double): Double = wattsHour * 1000 / 3.3
}