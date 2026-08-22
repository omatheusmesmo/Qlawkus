package dev.omatheusmesmo.qlawkus.metrics;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.time.Duration;
import java.util.List;

/**
 * Wraps the active-memory retriever so every turn reports what memory actually did: whether anything
 * was injected, how much, at what similarity, and how long the lookup took.
 *
 * <p>This is the metric the cognition subsystem was missing. Retrieval runs before every reply and
 * decides what the model remembers, yet "did memory work this turn?" could previously only be
 * answered by reading a log. A silent retrieval and a retrieval that returns nothing look identical
 * from outside, and the scores are what separate "nothing relevant existed" from "everything landed
 * just under min-score".
 *
 * <p>A decorator rather than an edit inside the augmentor: retrieval behaviour stays defined by
 * langchain4j's own retriever, and measurement cannot change what gets injected.
 */
public class MeteredContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final AgentMeters meters;

    public MeteredContentRetriever(ContentRetriever delegate, AgentMeters meters) {
        this.delegate = delegate;
        this.meters = meters;
    }

    @Override
    public List<Content> retrieve(Query query) {
        long startedAt = System.nanoTime();
        List<Content> contents = delegate.retrieve(query);
        meters.retrieval(contents.size(), scoresOf(contents), Duration.ofNanos(System.nanoTime() - startedAt));
        return contents;
    }

    /**
     * Reads each result's similarity from {@link ContentMetadata#SCORE}. The key is absent for
     * retrievers that do not score, so anything unscored is skipped rather than recorded as zero,
     * which would drag the distribution down and misrepresent the threshold.
     */
    private static double[] scoresOf(List<Content> contents) {
        return contents.stream()
                .map(content -> content.metadata().get(ContentMetadata.SCORE))
                .filter(Number.class::isInstance)
                .mapToDouble(score -> ((Number) score).doubleValue())
                .toArray();
    }
}
