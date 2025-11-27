package org.vechain.indexer.b3tr.action

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration for B3TR impact validation thresholds.
 *
 * Based on VeBetterDAO documentation:
 * https://docs.vebetterdao.org/developer-guides/sustainability-proof-and-impacts#categories
 *
 * Each impact type has its own threshold to account for different scales:
 * - carbon: grams of CO2 (default: 100,000g = 100 kg)
 * - water: milliliters (default: 1,000,000ml = 1,000 liters)
 * - energy: watt-hours (default: 1,000,000Wh = 1 MWh)
 * - waste_mass: grams (default: 100,000g = 100 kg)
 * - timber: grams (default: 100,000g = 100 kg)
 * - plastic: grams (default: 100,000g = 100 kg)
 * - education_time: seconds (default: 86,400s = 24 hours)
 * - trees_planted: number (default: 10,000 trees)
 * - calories_burned: kcal (default: 100,000 kcal)
 * - sleep_quality_percentage: percentage (default: 100%)
 * - clean_energy_production_wh: watt-hours (default: 1,000,000Wh = 1 MWh)
 */
@Configuration
@ConfigurationProperties("b3tr-impact-thresholds")
open class ActionImpactConfig {
    /** Carbon footprint in grams of CO2 (default: 100 kg) */
    var carbon: Long = 100_000

    /** Water conservation in milliliters (default: 1,000 L) */
    var water: Long = 1_000_000

    /** Energy conservation in watt-hours (default: 1 MWh) */
    var energy: Long = 1_000_000

    /** Waste mass in grams (default: 100 kg) */
    var wasteMass: Long = 100_000

    /** Timber conservation in grams (default: 100 kg) */
    var timber: Long = 100_000

    /** Plastic reduction in grams (default: 100 kg) */
    var plastic: Long = 100_000

    /** Education time in seconds (default: 24 hrs) */
    var educationTime: Long = 86_400

    /** Trees planted count (default: 10,000 trees) */
    var treesPlanted: Long = 10_000

    /** Calories burned in kcal (default: 100,000 kCal) */
    var caloriesBurned: Long = 100_000

    /** Sleep quality percentage (default: 100%) */
    var sleepQualityPercentage: Long = 100

    /** Clean energy production in watt-hours (default: 1 MWh) */
    var cleanEnergyProductionWh: Long = 1_000_000

    /** Legacy/deprecated fields, using high defaults to be permissive */
    var wasteItems: Long = 1_000_000
    var wasteReduction: Long = 1_000_000
    var biodiversity: Long = 1_000_000
    var people: Long = 1_000_000
}
