package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Postgres-backed {@link UserProfileStore}, active when {@code qlawkus.cognition.backend=pgvector}
 * (the default). The owner is the singleton {@code user_profile} row seeded by Flyway.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "pgvector", enableIfMissing = true)
public class PgUserProfileStore implements UserProfileStore {

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
}
