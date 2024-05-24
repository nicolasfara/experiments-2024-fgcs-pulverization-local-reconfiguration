package it.unibo.alchemist.boundary.swingui.effect.api

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.molecules.SimpleMolecule
import java.awt.Color
import java.awt.Graphics2D

@Suppress("DEPRECATION")
class PercentageBatteryEffect : Effect {
    @Deprecated("")
    override fun apply(graphic: Graphics2D?, node: Node<*>?, x: Int, y: Int) {
        node?.getConcentration(SimpleMolecule("SmartphonePercentage"))?.let { smartphoneCharge ->
            val wearableCharge = node.getConcentration(SimpleMolecule("WearablePercentage")) as Double
            val value = ((smartphoneCharge as Double + wearableCharge) / 2).toFloat() / 300
            val startx = x - 15 / 2
            val starty = y - 15 / 2
            graphic?.color = Color.getHSBColor(value, 1.0f, 1.0f)
            graphic?.fillOval(startx, starty, 15, 15)
        }
    }
    override fun getColorSummary(): Color? = Color.GREEN
}
