package com.vztechcore.vztune.core.domain.search

/**
 * Result of a search operation. Never thrown, always returned.
 */
sealed interface SearchResult {
    data class Success(val hits: List<SearchHit>) : SearchResult
    data class Failure(val message: String) : SearchResult
}