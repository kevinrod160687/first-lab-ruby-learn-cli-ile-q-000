package dev.kevin.trail.core.batch

import dev.kevin.trail.core.model.OutboxRecord
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.privacy.PrivateZoneFilter
import dev.kevin.trail.core.privacy.ZoneEffect

data class BatchPlan(
    val batchId: String,
    val toSend: List<OutboxRecord>,
    val suppressed: List<OutboxRecord>,
    val reusedBatchId: Boolean,
)

/**
 * Selects one idempotent batch.
 *
 * Records already tagged with a batch id are retried under that same id so a
 * webhook can ignore a duplicate delivery. New records are deduped on kind +
 * tst. Private zones are applied to the payload copy only.
 */
class BatchPlanner(
    private val zoneFilter: PrivateZoneFilter = PrivateZoneFilter(),
    private val newBatchId: () -> String,
) {
    fun plan(pending: List<OutboxRecord>, zones: List<PrivateZone>): BatchPlan? {
        if (pending.isEmpty()) return null
        val inFlightIds = pending.mapNotNull { it.batchId }.filter { it.isNotBlank() }.distinct()
        val (group, batchId, reused) = if (inFlightIds.isNotEmpty()) {
            val id = inFlightIds.min()
            Triple(pending.filter { it.batchId == id }, id, true)
        } else {
            Triple(pending.filter { it.batchId.isNullOrBlank() }, newBatchId(), false)
        }
        if (group.isEmpty()) return null
        return partition(group, zones, batchId, reused)
    }

    private fun partition(
        group: List<OutboxRecord>,
        zones: List<PrivateZone>,
        batchId: String,
        reused: Boolean,
    ): BatchPlan {
        val kept = group
            .groupBy { it.kind to it.tst }
            .map { (_, records) -> records.maxBy { it.id } }
        val duplicateIds = group.map { it.kind to it.id }.toSet() - kept.map { it.kind to it.id }.toSet()
        val suppressed = mutableListOf<OutboxRecord>()
        suppressed += group.filter { (it.kind to it.id) in duplicateIds }
        val toSend = mutableListOf<OutboxRecord>()
        for (record in kept.sortedWith(compareBy({ it.tst }, { it.kind.ordinal }))) {
            when (val effect = zoneFilter.effect(record.latitude, record.longitude, record.accuracyMeters, zones)) {
                ZoneEffect.Drop -> suppressed += record
                ZoneEffect.Keep -> toSend += record.copy(batchId = batchId)
                is ZoneEffect.Coarsen -> toSend += record.copy(
                    latitude = effect.latitude,
                    longitude = effect.longitude,
                    accuracyMeters = effect.accuracyMeters,
                    batchId = batchId,
                )
            }
        }
        return BatchPlan(batchId, toSend, suppressed, reused)
    }
}
