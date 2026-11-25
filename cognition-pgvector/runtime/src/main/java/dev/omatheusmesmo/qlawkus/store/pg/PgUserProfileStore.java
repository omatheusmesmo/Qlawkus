package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Postgres-backed {@link UserProfileStore}, active when {@code qlawkus.cognition.backend=pgvector}. The owner is the singleton {@code user_profile} row seeded by Flyway.
 */
@ApplicationScoped
@Typed(PgUserProfileStore.class)
public class PgUserProfileStore implements UserProfileStore {

  @Inject
  PgWriteProbe writeProbe;

  @Override
  @Transactional
  public UserProfile load() {
    UserProfileEntity entity = UserProfileEntity.findProfile();
    return entity == null ? null : entity.toDomain();
  }

  @Override
  @Transactional
  public void save(UserProfile profile) {
    UserProfileEntity entity = UserProfileEntity.findProfile();
    if (entity == null) {
      return;
    }
    entity.copyFrom(profile);
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.USER_PROFILE);
  }
}
