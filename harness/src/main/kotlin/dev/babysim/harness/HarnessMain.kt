package dev.babysim.harness

import dev.babysim.engine.EngineInfo

/**
 * Simulation harness CLI (docs/04 §10). M2.1: skeleton only; report generation arrives in M2.6.
 */
fun main(args: Array<String>) {
    println(banner())
    if (args.isNotEmpty()) {
        println("Arguments are not supported yet (M2.6): ${args.joinToString(" ")}")
    }
}

internal fun banner(): String = "baby-sim harness (engine schema v${EngineInfo.ENGINE_SCHEMA_VERSION})"
