package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.config.CognitionConfig;
import dev.omatheusmesmo.qlawkus.config.CognitionConfig.Backend;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The cognition backend this boot runs on, read once from {@code qlawkus.cognition.backend} (which
 * {@link StoreBackendConfigSourceFactory} fills in when nobody chose). Everything in this extension
 * that touches the database asks here first, so on {@code markdown} the extension stays on the
 * classpath and does nothing.
 */
@ApplicationScoped
public class CognitionBackendState {

  private final Backend backend;

  CognitionBackendState(CognitionConfig config) {
    this.backend = config.backend();
  }

  /** The backend chosen for this boot. */
  public Backend backend() {
    return backend;
  }

  /** Whether a PostgreSQL database backs this boot, which is both {@code pgvector} and {@code hybrid}. */
  public boolean usesPostgres() {
    return backend != Backend.MARKDOWN;
  }

  /** Whether files stay the source of truth and PostgreSQL mirrors them. */
  public boolean isHybrid() {
    return backend == Backend.HYBRID;
  }
}
