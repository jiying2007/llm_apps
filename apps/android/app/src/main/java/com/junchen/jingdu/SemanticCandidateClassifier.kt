package com.junchen.jingdu

enum class SemanticCandidateLabel { BODY, AD, UNCERTAIN }

data class SemanticCandidateDecision(
    val label: SemanticCandidateLabel,
    val confidence: Float,
)

/**
 * Future local-model seam. The contract is intentionally candidate-only: implementations may
 * classify a small Smart Clean candidate string, but must never receive a whole TXT document.
 */
internal fun interface SemanticCandidateClassifier {
    fun classifyCandidate(text: String): SemanticCandidateDecision
}

internal object DisabledSemanticCandidateClassifier : SemanticCandidateClassifier {
    override fun classifyCandidate(text: String) = SemanticCandidateDecision(
        label = SemanticCandidateLabel.UNCERTAIN,
        confidence = 0f,
    )
}
