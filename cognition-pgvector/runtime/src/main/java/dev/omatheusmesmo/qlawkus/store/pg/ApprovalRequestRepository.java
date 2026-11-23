package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Panache access to the {@code approval_request} table, shared by the pg and hybrid approval stores so
 * both write the same rows through one transactional seam. {@link #transition} takes a row lock before it
 * reads the status, so two callers racing to decide one request are serialized and only the first sees the
 * status it expected.
 */
@ApplicationScoped
public class ApprovalRequestRepository {

  private static final List<String> TERMINAL = Arrays.stream(ApprovalStatus.values())
      .filter(ApprovalStatus::isTerminal)
      .map(Enum::name)
      .toList();

  @Transactional
  public void insert(ApprovalRequest request) {
    if (ApprovalRequestEntity.findById(request.id()) != null) {
      throw new IllegalStateException("Approval request " + request.id() + " already exists");
    }
    ApprovalRequestEntity entity = new ApprovalRequestEntity();
    entity.id = request.id();
    entity.copyFrom(request);
    entity.persist();
  }

  @Transactional
  public void upsert(ApprovalRequest request) {
    ApprovalRequestEntity entity = ApprovalRequestEntity.findById(request.id());
    if (entity == null) {
      entity = new ApprovalRequestEntity();
      entity.id = request.id();
      entity.copyFrom(request);
      entity.persist();
    } else {
      entity.copyFrom(request);
    }
  }

  @Transactional
  public Optional<ApprovalRequest> find(String id) {
    ApprovalRequestEntity entity = ApprovalRequestEntity.findById(id);
    return Optional.ofNullable(entity).map(ApprovalRequestEntity::toRequest);
  }

  @Transactional
  public List<ApprovalRequest> listNewestFirst() {
    return ApprovalRequestEntity.<ApprovalRequestEntity>list("order by createdAt desc, id desc").stream()
        .map(ApprovalRequestEntity::toRequest)
        .toList();
  }

  @Transactional
  public Optional<ApprovalRequest> transition(String id, ApprovalStatus from,
      UnaryOperator<ApprovalRequest> change) {
    ApprovalRequestEntity entity = ApprovalRequestEntity.findById(id, LockModeType.PESSIMISTIC_WRITE);
    if (entity == null || !entity.status.equals(from.name())) {
      return Optional.empty();
    }
    ApprovalRequest next = change.apply(entity.toRequest());
    if (!id.equals(next.id())) {
      throw new IllegalArgumentException("A transition must keep the request id");
    }
    entity.copyFrom(next);
    return Optional.of(next);
  }

  @Transactional
  public void pruneTerminal(int keep) {
    List<ApprovalRequestEntity> settled = ApprovalRequestEntity.list(
        "status in ?1 order by createdAt desc, id desc", TERMINAL);
    settled.stream().skip(Math.max(0, keep)).forEach(ApprovalRequestEntity::delete);
  }

  @Transactional
  public void deleteAll() {
    ApprovalRequestEntity.deleteAll();
  }
}
