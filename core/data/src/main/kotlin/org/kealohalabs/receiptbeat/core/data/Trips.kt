package org.kealohalabs.receiptbeat.core.data

import java.time.LocalDate
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt

/** One trip or project with its receipts rolled up. */
data class TripSummary(
    val project: Project,
    val count: Int,
    val totalCents: Long,
    val first: LocalDate?,
    val last: LocalDate?,
    /** Receipts dated inside the trip that are not on any trip yet. */
    val unassignedInRange: List<String>,
)

object TripMath {
    fun summarize(projects: List<Project>, all: List<Receipt>): List<TripSummary> {
        val live = all.filter { it.deletedAt == null }
        val byTrip = live.groupBy { it.projectId }
        val loose = byTrip[null].orEmpty()
        return projects.map { p ->
            val list = byTrip[p.id].orEmpty()
            val dates = list.mapNotNull { it.purchaseDate }
            TripSummary(
                p,
                list.size,
                list.sumOf { ReportRepository.homeCents(it) },
                p.startDate ?: dates.minOrNull(),
                p.endDate ?: dates.maxOrNull(),
                loose.filter { inDates(p, it.purchaseDate) }.map { it.id },
            )
        }.sortedWith(compareByDescending<TripSummary> { it.last ?: it.first ?: LocalDate.MIN }.thenBy { it.project.name })
    }

    /** True when the trip has both dates set and the day falls between them. */
    fun inDates(p: Project, day: LocalDate?): Boolean {
        val s = p.startDate ?: return false
        val e = p.endDate ?: return false
        return day != null && !day.isBefore(s) && !day.isAfter(e)
    }
}
