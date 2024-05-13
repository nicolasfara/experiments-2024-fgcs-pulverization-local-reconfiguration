package it.unibo.alchemist.utils

import it.unibo.alchemist.model.molecules.SimpleMolecule
import kotlin.properties.ReadOnlyProperty

fun molecule(): ReadOnlyProperty<Any?, SimpleMolecule> = ReadOnlyProperty { _, property -> SimpleMolecule(property.name) }