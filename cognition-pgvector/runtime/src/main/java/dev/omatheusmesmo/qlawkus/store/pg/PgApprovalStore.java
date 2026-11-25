package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.approval.FileApprovalStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Postgres-backed {@link ApprovalStore}, active when {@code qlawkus.cognition.backend=pgvector}. Requests are rows in the {@code approval_request} table and a decision is a compare-and-set
 * under a row lock. Chosen at boot by {@link CognitionStoreSelector}.
 */
@ApplicationScoped
@Typed(PgApprovalStore.class)
public class PgApprovalStore implements ApprovalStore {

  private final ApprovalRequestRepository requests;
  private final PgWriteProbe writeProbe;

  @Inject
  public PgApprovalStore(ApprovalRequestRepository requests, PgWriteProbe writeProbe) {
    this.requests = requests;
    this.writeProbe = writeProbe;
  }

  @Override
  public void create(ApprovalRequest request) {
    requests.insert(request);
    requests.pruneTerminal(FileApprovalStore.DEFAULT_MAX_TERMINAL);
  }

  @Override
  public Optional<ApprovalRequest> find(String id) {
    if (!ApprovalRequest.isValidId(id)) {
      return Optional.empty();
    }
    return requests.find(id);
  }

  @Override
  public List<ApprovalRequest> list() {
    return requests.listNewestFirst();
  }

  @Override
  public Optional<ApprovalRequest> transition(String id, ApprovalStatus from,
      UnaryOperator<ApprovalRequest> change) {
    if (!ApprovalRequest.isValidId(id)) {
      return Optional.empty();
    }
    Optional<ApprovalRequest> next = requests.transition(id, from, change);
    next.ifPresent(settled -> requests.pruneTerminal(FileApprovalStore.DEFAULT_MAX_TERMINAL));
    return next;
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.APPROVAL_REQUEST);
  }
}
