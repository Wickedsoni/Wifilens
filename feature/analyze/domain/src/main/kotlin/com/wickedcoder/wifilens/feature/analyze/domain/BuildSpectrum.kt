package com.wickedcoder.wifilens.feature.analyze.domain

import kotlin.math.abs

/** Channels this close on 2.4 GHz overlap each other (5 MHz spacing, 20 MHz wide signals). */
private const val OVERLAP_CHANNEL_DISTANCE = 2

/**
 * Turns the scan list into the Spectrum tab's data for one [BandFilter]: a congestion bar per channel,
 * co-channel and overlapping counts for the connected network, and the best-channel advice.
 */
class BuildSpectrum {
    operator fun invoke(networks: List<ScannedNetwork>, band: BandFilter, connected: ConnectedNetwork?): SpectrumSummary {
        val connectedChannel = connected?.channel
        val inBand = networks.filter { it.band == band.label }
        val bars = inBand
            .groupBy { it.channel }
            .map { (channel, onChannel) ->
                SpectrumBar(
                    channel = channel,
                    congestionScore = onChannel.sumOf { rssiToCongestionContribution(it.rssiDbm) }.coerceAtMost(MAX_CONGESTION),
                    networkLabels = onChannel.map { it.ssid },
                    peakRssiDbm = onChannel.maxOf { it.rssiDbm },
                )
            }.sortedBy { it.channel }

        val coChannel = connectedChannel?.let { ch -> inBand.count { it.channel == ch } } ?: 0
        val overlapping = connectedChannel?.let { ch ->
            inBand.count { it.channel != ch && abs(it.channel - ch) <= OVERLAP_CHANNEL_DISTANCE }
        } ?: 0
        val strongestInterferer = inBand
            .filter { it.channel != connectedChannel }
            .maxOfOrNull { it.rssiDbm }

        return SpectrumSummary(
            bars = bars,
            stats = SpectrumStats(
                coChannelCount = coChannel,
                overlappingCount = overlapping,
                strongestInterfererDbm = strongestInterferer,
            ),
            advice = recommendChannel(networks, band, connected),
        )
    }
}
