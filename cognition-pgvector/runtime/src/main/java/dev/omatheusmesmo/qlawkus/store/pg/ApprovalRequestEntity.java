package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "approval_request")
public class ApprovalRequestEntity extends PanacheEntityBase {

  @Id
  @Column(length = 32)
  public String id;

  @Column(nullable = false)
  public String toolName;

  @Column(columnDefinition = "TEXT", nullable = false)
  public String arguments;

  @Column(nullable = false, length = 64)
  public String bindingHash;

  @Column(columnDefinition = "TEXT", nullable = false)
  public String preview;

  public String memoryId;

  public String providerId;

  public String chatId;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant decidedAt;

  public String decidedBy;

  @Column(columnDefinition = "TEXT")
  public String resultSummary;

  void copyFrom(ApprovalRequest request) {
    toolName = request.toolName();
    arguments = request.arguments();
    bindingHash = request.bindingHash();
    preview = request.preview();
    memoryId = request.memoryId();
    providerId = request.providerId();
    chatId = request.chatId();
    status = request.status().name();
    createdAt = request.createdAt();
    expiresAt = request.expiresAt();
    decidedAt = request.decidedAt();
    decidedBy = request.decidedBy();
    resultSummary = request.resultSummary();
  }

  ApprovalRequest toRequest() {
    return new ApprovalRequest(id, toolName, arguments, bindingHash, preview, memoryId, providerId, chatId,
        ApprovalStatus.valueOf(status), createdAt, expiresAt, decidedAt, decidedBy, resultSummary);
  }
}
