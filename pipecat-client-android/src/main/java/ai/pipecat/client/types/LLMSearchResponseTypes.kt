package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Search entry point from bot LLM search response
 */
@Serializable
data class SearchEntryPoint(
    @SerialName("rendered_content")
    val renderedContent: String? = null
)

/**
 * Web source information from bot LLM search response
 */
@Serializable
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

/**
 * Grounding chunk information
 */
@Serializable
data class GroundingChunk(
    val web: WebSource? = null
)

/**
 * Grounding segment information
 */
@Serializable
data class GroundingSegment(
    @SerialName("part_index")
    val partIndex: Int? = null,
    @SerialName("start_index")
    val startIndex: Int? = null,
    @SerialName("end_index")
    val endIndex: Int? = null,
    val text: String? = null
)

/**
 * Grounding support information
 */
@Serializable
data class GroundingSupport(
    val segment: GroundingSegment? = null,
    @SerialName("grounding_chunk_indices")
    val groundingChunkIndices: List<Int>? = null,
    @SerialName("confidence_scores")
    val confidenceScores: List<Double>? = null
)

@Serializable
data class BotLLMSearchResponseData(
    @SerialName("search_entry_point")
    val searchEntryPoint: SearchEntryPoint? = null,
    @SerialName("grounding_chunks")
    val groundingChunks: List<GroundingChunk>? = null,
    @SerialName("grounding_supports")
    val groundingSupports: List<GroundingSupport>? = null,
    @SerialName("web_search_queries")
    val webSearchQueries: List<String>? = null
)