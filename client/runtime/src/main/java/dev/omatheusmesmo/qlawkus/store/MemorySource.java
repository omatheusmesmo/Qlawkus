package dev.omatheusmesmo.qlawkus.store;

/**
 * Provenance tag stored in a fact's {@code source} metadata. Centralizes the values so
 * producers (tools, observers, jobs) and admin purges cannot drift out of sync.
 */
public enum MemorySource {

  REMEMBER_TOOL("remember-tool"),
  SEMANTIC_EXTRACTOR("semantic-extractor"),
  EPISODIC_CONSOLIDATOR("episodic-consolidator");

  private final String value;

  MemorySource(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
