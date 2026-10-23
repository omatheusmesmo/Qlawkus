package dev.omatheusmesmo.qlawkus.store.pg;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PgWriteProbe} that needs no database: it records which tables a store asked about and,
 * when built with a refusal, answers every question with it.
 */
class RecordingWriteProbe extends PgWriteProbe {

  final List<Table> probed = new ArrayList<>();
  private final String refusal;

  RecordingWriteProbe() {
    this(null);
  }

  RecordingWriteProbe(String refusal) {
    this.refusal = refusal;
  }

  @Override
  public void verify(Table table) {
    probed.add(table);
    if (refusal != null) {
      throw new IllegalStateException(refusal);
    }
  }
}
