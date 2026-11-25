package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Postgres-backed {@link SoulStore}, active when {@code qlawkus.cognition.backend=pgvector}. The persona is the singleton {@code soul} row seeded by Flyway.
 */
@ApplicationScoped
@Typed(PgSoulStore.class)
public class PgSoulStore implements SoulStore {

  @Inject
  PgWriteProbe writeProbe;

  @Override
  @Transactional
  public Soul load() {
    SoulEntity entity = SoulEntity.findSoul();
    return entity == null ? null : entity.toDomain();
  }

  @Override
  @Transactional
  public void save(Soul soul) {
    SoulEntity entity = SoulEntity.findSoul();
    if (entity == null) {
      return;
    }
    entity.copyFrom(soul);
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.SOUL);
  }
}
