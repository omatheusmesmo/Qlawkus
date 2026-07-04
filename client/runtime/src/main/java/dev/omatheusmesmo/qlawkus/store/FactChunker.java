package dev.omatheusmesmo.qlawkus.store;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits fact content into embedding-sized segments so a single fact never exceeds the embedding
 * model's token limit (for example 512 tokens for {@code nv-embedqa-e5-v5}). One fact becomes N
 * segments (N is 1 when it already fits); every segment carries the parent {@link #FACT_HASH} so
 * the stores deduplicate and reconcile at fact granularity, and segment 0 additionally carries the
 * whole {@link #FACT_TEXT} so the pgvector backend can reconstruct the fact when mirroring it back
 * to files. The split is character-based because the embedding model's tokenizer is not on the
 * classpath; the budget is a conservative proxy configured by {@code qlawkus.agent.facts.chunk-*}.
 */
public class FactChunker {

  /** MD5 of the whole fact content, shared by every segment of one fact: its stable identity. */
  public static final String FACT_HASH = "factHash";
  /** Zero-based position of this segment within its fact. */
  public static final String CHUNK_INDEX = "chunkIndex";
  /** Total number of segments the fact was split into. */
  public static final String CHUNK_COUNT = "chunkCount";
  /** The whole fact content, present only on segment 0, so files can be rebuilt from pgvector. */
  public static final String FACT_TEXT = "factText";

  private final DocumentSplitter splitter;

  public FactChunker(int maxChars, int overlapChars) {
    this.splitter = DocumentSplitters.recursive(maxChars, overlapChars);
  }

  /** A single embedding segment: the text to embed and the metadata to store alongside it. */
  public record Chunk(String text, Map<String, String> metadata) {
  }

  /** MD5 of the whole content: the fact's stable identity across files and pgvector. */
  public static String factHash(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }

  /**
   * Splits {@code content} into embedding segments, each tagged with the shared {@code factHash},
   * its {@code chunkIndex}/{@code chunkCount}, and (segment 0 only) the whole {@code factText}. The
   * supplied {@code baseMetadata} (source, memoryId, ...) is copied onto every segment.
   */
  public List<Chunk> chunk(String content, Map<String, String> baseMetadata) {
    String hash = factHash(content);
    List<String> texts = split(content);
    List<Chunk> chunks = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i++) {
      Map<String, String> metadata = new LinkedHashMap<>(baseMetadata);
      metadata.put(FACT_HASH, hash);
      metadata.put(CHUNK_INDEX, Integer.toString(i));
      metadata.put(CHUNK_COUNT, Integer.toString(texts.size()));
      if (i == 0) {
        metadata.put(FACT_TEXT, content);
      }
      chunks.add(new Chunk(texts.get(i), metadata));
    }
    return chunks;
  }

  private List<String> split(String content) {
    if (content == null || content.isBlank()) {
      return List.of(content == null ? "" : content);
    }
    List<TextSegment> segments = splitter.split(Document.from(content));
    if (segments.isEmpty()) {
      return List.of(content);
    }
    return segments.stream().map(TextSegment::text).toList();
  }
}
