package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.FAIR_SIGNAL_DBM
import com.wickedcoder.wifilens.core.model.GOOD_SIGNAL_DBM
import com.wickedcoder.wifilens.core.model.SecurityIssue

/** How much an insight matters. [Good] confirms something is fine; [Tip] is optional; the rest need action. */
enum class InsightSeverity { Good, Tip, Warning, Problem }

/** One finding of the health check. Data only: the UI turns each into a sentence and a concrete fix. */
sealed interface Insight {
    val severity: InsightSeverity

    data class Signal(val rssiDbm: Int, override val severity: InsightSeverity) : Insight

    /** The connected channel is busy; [suggestedChannel] is the quietest alternative on the same band, if any. */
    data class CrowdedChannel(
        val channel: Int,
        val band: String,
        val congestionScore: Int,
        val suggestedChannel: Int?,
        val suggestedIsDfs: Boolean,
        override val severity: InsightSeverity,
    ) : Insight

    /** On 2.4 GHz while the same network is available on a faster band with usable signal. */
    data class BetterBand(val band: String, val rssiDbm: Int) : Insight {
        override val severity get() = InsightSeverity.Tip
    }

    /** Another access point of the same network (mesh node, extender) is clearly stronger on the same band. */
    data class StrongerAccessPoint(val bssidMasked: String, val rssiDbm: Int, val currentRssiDbm: Int) : Insight {
        override val severity get() = InsightSeverity.Tip
    }

    /** The network is served by several access points (a mesh or extenders). Informational. */
    data class MeshDetected(val accessPointCount: Int) : Insight {
        override val severity get() = InsightSeverity.Good
    }

    data class Security(val issue: SecurityIssue) : Insight {
        override val severity get() = when (issue) {
            SecurityIssue.Open, SecurityIssue.Wep -> InsightSeverity.Problem
            SecurityIssue.LegacyWpa, SecurityIssue.Tkip -> InsightSeverity.Warning
            SecurityIssue.Wpa3Transition -> InsightSeverity.Tip
        }
    }

    data class Speed(val downloadMbps: Float, override val severity: InsightSeverity) : Insight
}

/** Congestion (0–100) on the connected channel from which it's worth acting. */
private const val BUSY_CHANNEL = 45
private const val CROWDED_CHANNEL = 75

/** A faster band is only suggested if its signal here is at least fair. */
private val USABLE_BETTER_BAND_DBM = FAIR_SIGNAL_DBM.toInt()

/** Another access point must beat the current one by this much before switching is worth suggesting. */
private const val STRONGER_AP_MARGIN_DB = 10

/** BSSIDs sharing the first five octets are usually one device's radios (one per band), not separate APs. */
private const val DEVICE_PREFIX_LENGTH = 14

/** Download speeds below these (Mbps) struggle with several HD streams or video calls at once. */
private const val SLOW_SPEED_MBPS = 25f
private const val VERY_SLOW_SPEED_MBPS = 5f

/**
 * The one-tap health check: every rule applied to the current connection and the latest scan, worst first. Empty
 * when not connected (there's nothing of "yours" to check). [downloadMbps] is the speed test run with the check,
 * if any.
 */
fun buildInsights(connected: ConnectedNetwork?, networks: List<ScannedNetwork>, downloadMbps: Float? = null): List<Insight> {
    if (connected == null) return emptyList()
    val ownNetwork = connected.ssid?.let { ssid -> networks.filter { it.ssid == ssid } }.orEmpty()
    return buildList {
        add(signalInsight(connected.rssiDbm))
        crowdedChannelInsight(connected, networks)?.let(::add)
        betterBandInsight(connected, ownNetwork)?.let(::add)
        strongerAccessPointInsight(connected, ownNetwork)?.let(::add)
        meshInsight(ownNetwork)?.let(::add)
        addAll(securityInsights(ownNetwork))
        downloadMbps?.let { add(speedInsight(it)) }
    }.sortedByDescending { it.severity }
}

/** Worst severity among [insights]; [InsightSeverity.Good] when there's nothing to act on. */
fun overallSeverity(insights: List<Insight>): InsightSeverity = insights.maxOfOrNull { it.severity } ?: InsightSeverity.Good

internal fun signalInsight(rssiDbm: Int): Insight.Signal = Insight.Signal(
    rssiDbm,
    when {
        rssiDbm >= GOOD_SIGNAL_DBM -> InsightSeverity.Good
        rssiDbm >= FAIR_SIGNAL_DBM -> InsightSeverity.Warning
        else -> InsightSeverity.Problem
    },
)

internal fun crowdedChannelInsight(connected: ConnectedNetwork, networks: List<ScannedNetwork>): Insight.CrowdedChannel? {
    val band = BandFilter.entries.firstOrNull { it.label == connected.band && it != BandFilter.All } ?: return null
    val others = networks.filter {
        it.band == connected.band && it.id != connected.bssid &&
            !(it.ssid == connected.ssid && it.channel == connected.channel)
    }
    val score = channelCongestion(connected.channel, band, others)
    if (score < BUSY_CHANNEL) return null
    val switch = recommendChannel(networks, band, connected) as? ChannelAdvice.Switch
    return Insight.CrowdedChannel(
        channel = connected.channel,
        band = connected.band,
        congestionScore = score,
        suggestedChannel = switch?.channel,
        suggestedIsDfs = switch?.isDfs == true,
        severity = if (score >= CROWDED_CHANNEL) InsightSeverity.Problem else InsightSeverity.Warning,
    )
}

internal fun betterBandInsight(connected: ConnectedNetwork, ownNetwork: List<ScannedNetwork>): Insight.BetterBand? {
    if (connected.band != BandFilter.Band24.label) return null
    val faster = ownNetwork
        .filter { it.band != BandFilter.Band24.label && it.rssiDbm >= USABLE_BETTER_BAND_DBM }
        .maxByOrNull { it.rssiDbm } ?: return null
    return Insight.BetterBand(faster.band, faster.rssiDbm)
}

internal fun strongerAccessPointInsight(connected: ConnectedNetwork, ownNetwork: List<ScannedNetwork>): Insight.StrongerAccessPoint? {
    val current = connected.bssid ?: return null
    val stronger = ownNetwork
        .filter { it.band == connected.band && it.id != current && it.id.take(DEVICE_PREFIX_LENGTH) != current.take(DEVICE_PREFIX_LENGTH) }
        .maxByOrNull { it.rssiDbm }
        ?.takeIf { it.rssiDbm >= connected.rssiDbm + STRONGER_AP_MARGIN_DB } ?: return null
    return Insight.StrongerAccessPoint(stronger.bssidMasked, stronger.rssiDbm, connected.rssiDbm)
}

internal fun meshInsight(ownNetwork: List<ScannedNetwork>): Insight.MeshDetected? {
    val devices = ownNetwork.map { it.id.take(DEVICE_PREFIX_LENGTH) }.distinct().size
    return if (devices >= 2) Insight.MeshDetected(devices) else null
}

/** Issues of your own network, once each (every access point of a network normally shares one setup). */
internal fun securityInsights(ownNetwork: List<ScannedNetwork>): List<Insight.Security> =
    ownNetwork
        .flatMap { it.securityIssues }
        .distinct()
        .sorted()
        .map(Insight::Security)

internal fun speedInsight(downloadMbps: Float): Insight.Speed = Insight.Speed(
    downloadMbps,
    when {
        downloadMbps < VERY_SLOW_SPEED_MBPS -> InsightSeverity.Problem
        downloadMbps < SLOW_SPEED_MBPS -> InsightSeverity.Warning
        else -> InsightSeverity.Good
    },
)
