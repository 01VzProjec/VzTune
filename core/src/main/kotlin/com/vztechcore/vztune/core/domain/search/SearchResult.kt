package com.vztechcore.vztune.core.domain.search

sealed interface SearchResult {
    data class Success(
        val hits: List<SearchHit>,
        val hasMore: Boolean,
    ) : SearchResult

    data class Failure(val message: String) : SearchResult
}