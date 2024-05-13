package it.unibo.alchemist.model

class SmartphoneConsumptionModel<T>(
    override val node: Node<T>,
    override val deviceEnergyPerInstruction: Double
) : ConsumptionModel<T>() {
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> {
        TODO("Not yet implemented")
    }
}