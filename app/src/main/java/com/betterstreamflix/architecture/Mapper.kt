package com.betterstreamflix.architecture

/**
 * Mapper — provides a standard interface for mapping between
 * domain models, entities, and DTOs.
 */
interface Mapper<From, To> {
    fun map(from: From): To
    fun mapBack(to: To): From
    fun mapList(from: List<From>): List<To> = from.map { map(it) }
    fun mapListBack(to: List<To>): List<From> = to.map { mapBack(it) }
}

/**
 * One-way mapper — for cases where reverse mapping is not needed.
 */
interface OneWayMapper<From, To> {
    fun map(from: From): To
    fun mapList(from: List<From>): List<To> = from.map { map(it) }
}

/**
 * Null-safe mapper wrapper.
 */
class SafeMapper<From, To>(private val mapper: Mapper<From, To>) : Mapper<From?, To?> {
    override fun map(from: From?): To? = from?.let { mapper.map(it) }
    override fun mapBack(to: To?): From? = to?.let { mapper.mapBack(it) }
}

/**
 * Composite mapper — chains multiple mappers.
 */
class CompositeMapper<A, B, C>(
    private val first: Mapper<A, B>,
    private val second: Mapper<B, C>,
) : Mapper<A, C> {
    override fun map(from: A): C = second.map(first.map(from))
    override fun mapBack(to: C): A = first.mapBack(second.mapBack(to))
}
