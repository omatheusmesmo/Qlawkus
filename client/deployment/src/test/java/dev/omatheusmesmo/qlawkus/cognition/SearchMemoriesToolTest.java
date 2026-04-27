package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.omatheusmesmo.qlawkus.repository.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@QuarkusTest
class SearchMemoriesToolTest {

  @Inject
  SearchMemoriesTool searchMemoriesTool;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  static final String[] FACTS = {
    "User prefers Vim over VS Code for editing",
    "User codes in Rust and dislikes Java verbosity",
    "User works with Kubernetes and Docker for deployment",
    "User prefers dark theme in IDE",
    "User's name is Matheus",
    "User likes minimal design and clean architecture",
    "User deploys on Fridays is forbidden at their company",
    "User prefers constructor injection over field injection",
    "User uses Quarkus framework for backend services",
    "User dislikes var keyword in Java"
  };

  @BeforeAll
  static void checkFacts() {
    assert FACTS.length == 10;
  }

  @AfterEach
  @Transactional
  void cleanup() {
    embeddingRepository.deleteAll();
  }

  void seedFacts() {
    Metadata metadata = new Metadata();
    metadata.put("source", "semantic-extractor");
    for (String fact : FACTS) {
      TextSegment seg = TextSegment.from(fact, metadata);
      embeddingStore.add(embeddingModel.embed(seg).content(), seg);
    }
  }

  @Test
  void searchMemories_returnsRelevantFactsForEditorQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("code editor preference");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("vim")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForLanguageQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("programming language Rust");
    assertFalse(results.isEmpty());
  }

  @Test
  void searchMemories_returnsRelevantFactsForDeploymentQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("deployment policy Friday restriction");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("friday") || f.toLowerCase().contains("deploy")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForThemeQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("IDE appearance settings");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("dark theme")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForNameQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("what is my name");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("matheus")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForFrameworkQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("backend framework choice");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("quarkus")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForVarKeywordQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("Java variable declarations");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("var")));
  }

  @Test
  void searchMemories_returnsRelevantFactsForInjectionQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("dependency injection pattern");
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("constructor")));
  }

  @Test
  void searchMemories_filtersUnrelatedPhysicsQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("quantum physics entanglement");
    assertTrue(results.isEmpty());
  }

  @Test
  void searchMemories_filtersUnrelatedCookingQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("cooking recipe pasta carbonara");
    assertTrue(results.isEmpty());
  }

  @Test
  void searchMemories_filtersUnrelatedFootballQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("football world cup winners");
    assertTrue(results.isEmpty());
  }

  @Test
  void searchMemories_filtersUnrelatedPaintingQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("painting techniques watercolor");
    assertTrue(results.isEmpty());
  }

  @Test
  void searchMemories_filtersUnrelatedYogaQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("yoga meditation breathing techniques");
    assertTrue(results.isEmpty());
  }

  @Test
  void searchMemories_filtersUnrelatedRealEstateQuery() {
    seedFacts();
    List<String> results = searchMemoriesTool.searchMemories("real estate investment strategy");
    assertTrue(results.isEmpty());
  }
}
