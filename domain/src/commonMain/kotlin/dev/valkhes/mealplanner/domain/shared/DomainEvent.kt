package dev.valkhes.mealplanner.domain.shared

/**
 * A fact that happened. Events are the only data kept for good: they are never modified, and every
 * other structure is rebuilt from them (README, "Data that survives iteration").
 */
interface DomainEvent
