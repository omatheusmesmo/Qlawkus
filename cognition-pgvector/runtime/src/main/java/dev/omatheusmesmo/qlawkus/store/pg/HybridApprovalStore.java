package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.approval.FileApprovalStore;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Hybrid {@link ApprovalStore}, active when {@code qlawkus.cognition.backend=hybrid}. Each request is
 * written to its file (the source of truth, where the decision is taken) and mirrored into the
 * {@code approval_request} table; reads come from the files. A mirror failure is logged and never undoes
 * or blocks a decision the files already recorded, because the decision gates an external effect.
 */
@ApplicationScoped
@Typed(HybridApprovalStore.class)
public class HybridApprovalStore implements ApprovalStore {

  private final FileApprovalStore files;
  private final ApprovalRequestRepository mirror;
  private final PgWriteProbe writeProbe;

  @Inject
  public HybridApprovalStore(AgentConfig config, ApprovalRequestRepository mirror, PgWriteProbe writeProbe) {
    this(Path.of(config.state().root(), FileApprovalStore.DIRECTORY), FileApprovalStore.DEFAULT_MAX_TERMINAL,
        mirror, writeProbe);
  }

  HybridApprovalStore(Path directory, int maxTerminal, ApprovalRequestRepository mirror,
      PgWriteProbe writeProbe) {
    this.files = new FileApprovalStore(directory, maxTerminal);
    this.mirror = mirror;
    this.writeProbe = writeProbe;
  }

  @Override
  public void create(ApprovalRequest request) {
    files.create(request);
    mirrored(request);
  }

  @Override
  public Optional<ApprovalRequest> find(String id) {
    return files.find(id);
  }

  @Override
  public List<ApprovalRequest> list() {
    return files.list();
  }

  @Override
  public Optional<ApprovalRequest> transition(String id, ApprovalStatus from,
      UnaryOperator<ApprovalRequest> change) {
    Optional<ApprovalRequest> next = files.transition(id, from, change);
    next.ifPresent(this::mirrored);
    return next;
  }

  @Override
  public void verifyWritable() {
    files.verifyWritable();
    writeProbe.verify(PgWriteProbe.Table.APPROVAL_REQUEST);
  }

  private void mirrored(ApprovalRequest request) {
    try {
      mirror.upsert(request);
    } catch (RuntimeException e) {
      Log.warnf("Approval request %s was recorded in its file but not mirrored to the table (%s)", request.id(),
          e.getClass().getSimpleName());
    }
  }
}
