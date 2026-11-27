package dev.omatheusmesmo.qlawkus.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.approval.ApprovalGate;
import dev.omatheusmesmo.qlawkus.approval.ApprovalReasons;
import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The only writer of the permission policy. Every surface that changes it (the REST API, the
 * {@code /permissions} chat command, and the console and chat buttons later) is a thin frontend over
 * these methods, and no tool exposes any of them, so the model has no path to a change.
 *
 * <p>Changes are asymmetric. Tightening ({@code allow} to {@code ask}, {@code ask} to {@code deny},
 * {@code allow} to {@code deny}) applies at once for anyone, since being stricter has no risk. Loosening
 * applies at once only for an {@link Actor} that {@linkplain Actor#mayLoosen() may}, which is the
 * authenticated admin; for a chat sender it is recorded as an owner approval request and applied
 * exactly once when the owner decides it, after re-checking that the policy still holds what the
 * request was made against.
 */
@ApplicationScoped
public class PermissionService {

    /** The group name that addresses the operation default row instead of one group. */
    public static final String DEFAULT_GROUP = "default";

    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Who is asking, as recorded in the audit log.
     *
     * @param name the identity: an admin API user or a chat account
     * @param channel {@code rest} or the messaging provider
     * @param mayLoosen whether this actor's own authority is enough to loosen the policy
     */
    public record Actor(String name, String channel, boolean mayLoosen) {

        public static Actor admin(String name) {
            return new Actor(name, "rest", true);
        }

        public static Actor chat(String providerId, String userId) {
            return new Actor("messaging:" + providerId + ":" + userId, providerId, false);
        }
    }

    public enum Status {
        APPLIED,
        UNCHANGED,
        PENDING,
        REJECTED
    }

    /**
     * The result of a requested change.
     *
     * @param requestId the approval request, when the change waits for the owner
     */
    public record Outcome(Status status, String message, String requestId) {
    }

    /** Why a tool is decided the way it is, for the owner. */
    public record Explanation(String tool, String group, AccessClass accessClass, PermissionLevel level,
            List<String> reasons) {
    }

    private record Change(String kind, String target, UnaryOperator<PermissionState> apply, String before,
            String after, String preview, ObjectNode arguments) {
    }

    private final PermissionPolicy policy;
    private final PermissionStateFile file;
    private final PermissionAuditLog audit;
    private final ApprovalGate approvals;
    private final ApprovalReasons approvalReasons;
    private final Clock clock;

    @Inject
    public PermissionService(PermissionPolicy policy, PermissionsConfig config, AgentConfig agent,
            ApprovalGate approvals, ApprovalReasons approvalReasons) {
        this(policy, new PermissionStateFile(Path.of(agent.state().root(), PermissionStateFile.DIRECTORY)),
                new PermissionAuditLog(Path.of(agent.state().root(), PermissionStateFile.DIRECTORY),
                        config.auditRetention()), approvals, approvalReasons, Clock.systemUTC());
    }

    public PermissionService(PermissionPolicy policy, PermissionStateFile file, PermissionAuditLog audit,
            ApprovalGate approvals, ApprovalReasons approvalReasons, Clock clock) {
        this.policy = policy;
        this.file = file;
        this.audit = audit;
        this.approvals = approvals;
        this.approvalReasons = approvalReasons;
        this.clock = clock;
    }

    /**
     * Sets one cell, or the operation default when {@code group} is {@value #DEFAULT_GROUP}.
     */
    public synchronized Outcome set(String group, String operation, String level, Actor actor,
            AgentDeliveryContext.Snapshot origin) {
        Optional<AccessClass> parsedOperation = PermissionConfigReader.parseOperation(operation);
        Optional<PermissionLevel> parsedLevel = PermissionLevel.parse(level);
        if (parsedOperation.isEmpty()) {
            return rejected("'" + operation + "' is not an operation; use read, create, update, delete, send or execute");
        }
        if (parsedLevel.isEmpty()) {
            return rejected("'" + level + "' is not a level; use allow, ask or deny");
        }
        String normalizedGroup = group == null ? "" : group.strip().toLowerCase(Locale.ROOT);
        boolean defaultRow = DEFAULT_GROUP.equals(normalizedGroup);
        if (!defaultRow && !policy.matrix().groups().contains(normalizedGroup)) {
            return rejected("'" + group + "' is not a known group; known groups: "
                    + String.join(", ", policy.matrix().groups()) + ", or '" + DEFAULT_GROUP + "'");
        }
        AccessClass accessClass = parsedOperation.get();
        PermissionLevel wanted = parsedLevel.get();
        PermissionLevel current = defaultRow
                ? policy.matrix().operationDefault(accessClass).level()
                : policy.matrix().cell(normalizedGroup, accessClass).level();
        String target = normalizedGroup + "." + name(accessClass);
        ObjectNode arguments = JSON.createObjectNode();
        arguments.put("kind", "cell");
        arguments.put("group", normalizedGroup);
        arguments.put("operation", name(accessClass));
        arguments.put("level", wanted.label());
        arguments.put("from", current.label());
        Change change = new Change("cell", target,
                state -> defaultRow ? state.withDefault(accessClass, wanted)
                        : state.withCell(normalizedGroup, accessClass, wanted),
                current.label(), wanted.label(),
                "Permission change: " + target + ": " + current.label() + " -> " + wanted.label(), arguments);
        return submit(change, actor, origin, current == wanted);
    }

    /** Sets the exception for one tool. */
    public synchronized Outcome setTool(String tool, String level, Actor actor, AgentDeliveryContext.Snapshot origin) {
        Optional<PermissionLevel> parsedLevel = PermissionLevel.parse(level);
        if (tool == null || !TOOL_NAME.matcher(tool).matches()) {
            return rejected("'" + tool + "' is not a tool name");
        }
        if (parsedLevel.isEmpty()) {
            return rejected("'" + level + "' is not a level; use allow, ask or deny");
        }
        PermissionLevel wanted = parsedLevel.get();
        PermissionLevel current = policy.levelOf(tool);
        ObjectNode arguments = JSON.createObjectNode();
        arguments.put("kind", "tool");
        arguments.put("tool", tool);
        arguments.put("level", wanted.label());
        arguments.put("from", current.label());
        Change change = new Change("tool", "tool:" + tool, state -> state.withTool(tool, wanted), current.label(),
                wanted.label(), "Permission change: tool " + tool + ": " + current.label() + " -> " + wanted.label(),
                arguments);
        return submit(change, actor, origin, current == wanted);
    }

    /** Replaces the runtime policy with a preset's operation defaults. */
    public synchronized Outcome applyPreset(String name, Actor actor, AgentDeliveryContext.Snapshot origin) {
        Optional<Map<AccessClass, PermissionLevel>> levels = PermissionPresets.find(name);
        if (levels.isEmpty()) {
            return rejected("'" + name + "' is not a preset; use " + String.join(", ", PermissionPresets.names()));
        }
        String presetName = name.strip().toLowerCase(Locale.ROOT);
        PermissionState before = policy.runtime();
        ObjectNode arguments = JSON.createObjectNode();
        arguments.put("kind", "preset");
        arguments.put("preset", presetName);
        arguments.put("revision", before.revision());
        Change change = new Change("preset", "preset", state -> state.withPreset(presetName, levels.get()),
                describe(before), presetName, "Permission change: apply the " + presetName + " preset", arguments);
        return submit(change, actor, origin, false);
    }

    /** Drops every runtime change, back to the configuration and the extension suggestions. */
    public synchronized Outcome reset(Actor actor, AgentDeliveryContext.Snapshot origin) {
        PermissionState before = policy.runtime();
        ObjectNode arguments = JSON.createObjectNode();
        arguments.put("kind", "reset");
        arguments.put("revision", before.revision());
        Change change = new Change("reset", "reset", PermissionState::cleared, describe(before), "defaults",
                "Permission change: reset every runtime change", arguments);
        return submit(change, actor, origin, before.isEmpty());
    }

    /**
     * Applies a change the owner approved. It re-checks that the policy still holds what the request was
     * made against and throws when it does not, so a stale approval changes nothing.
     */
    public synchronized String applyApproved(ApprovalRequest request) {
        JsonNode arguments;
        try {
            arguments = JSON.readTree(request.arguments());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the stored change cannot be read");
        }
        Actor proposer = new Actor(arguments.path("actor").asText(null), arguments.path("channel").asText(null), false);
        Change change = rebuild(arguments);
        if (arguments.has("from")) {
            String now = currentLabel(arguments);
            if (!now.equals(arguments.path("from").asText())) {
                throw new IllegalStateException("the policy changed since this was asked (it is now " + now
                        + ", it was " + arguments.path("from").asText() + "); ask again");
            }
        } else if (arguments.path("revision").asLong(-1) != policy.runtime().revision()) {
            throw new IllegalStateException("the policy changed since this was asked; ask again");
        }
        apply(change, proposer, request.decidedBy(), request.id());
        return change.preview().replaceFirst("^Permission change: ", "Applied: ");
    }

    /** Why the tool is decided the way it is. */
    public Explanation explain(String tool) {
        PermissionMatrix.ToolResolution resolution = policy.resolve(tool);
        List<String> reasons = new ArrayList<>();
        reasons.add(resolution.decision().basis().substring(0, 1).toUpperCase(Locale.ROOT)
                + resolution.decision().basis().substring(1) + ": " + resolution.level().label() + ".");
        PermissionLevel level = resolution.level();
        Optional<String> legacy = approvalReasons.reasonFor(tool);
        if (legacy.isPresent() && level == PermissionLevel.ALLOW) {
            level = PermissionLevel.ASK;
            reasons.add("It still asks because it is " + legacy.get() + ".");
        } else if (legacy.isPresent()) {
            reasons.add("It is also " + legacy.get() + ".");
        }
        if (policy.matrix().registry().find(tool).isEmpty()) {
            reasons.add("No build-time class is known for it (a remote tool, for example), so it is treated as execute.");
        }
        return new Explanation(tool, resolution.group(), resolution.accessClass(), level, List.copyOf(reasons));
    }

    /** The whole matrix as it stands now. */
    public PermissionOverview overview() {
        PermissionMatrix matrix = policy.matrix();
        Map<AccessClass, PermissionOverview.Cell> defaults = new EnumMap<>(AccessClass.class);
        List<PermissionOverview.Group> groups = new ArrayList<>();
        for (AccessClass operation : AccessClass.values()) {
            PermissionMatrix.Decision decision = matrix.operationDefault(operation);
            defaults.put(operation, new PermissionOverview.Cell(decision.level(), decision.basis(), 0));
        }
        for (String group : matrix.groups()) {
            Map<AccessClass, PermissionOverview.Cell> cells = new EnumMap<>(AccessClass.class);
            for (AccessClass operation : AccessClass.values()) {
                PermissionMatrix.Decision decision = matrix.cell(group, operation);
                cells.put(operation, new PermissionOverview.Cell(decision.level(), decision.basis(),
                        matrix.toolCount(group, operation)));
            }
            groups.add(new PermissionOverview.Group(group, cells));
        }
        Map<String, PermissionLevel> tools = new TreeMap<>(policy.runtime().tools());
        policy.configured().tools().forEach(tools::putIfAbsent);
        return new PermissionOverview(policy.runtime().preset() != null ? policy.runtime().preset()
                : policy.configured().preset(), defaults, groups, tools);
    }

    /** Every recorded change, newest first. */
    public List<PermissionAuditEntry> audit() {
        return audit.list();
    }

    private Outcome submit(Change change, Actor actor, AgentDeliveryContext.Snapshot origin, boolean unchanged) {
        if (unchanged) {
            return new Outcome(Status.UNCHANGED, "Nothing to change: " + change.target() + " is already "
                    + change.after() + ".", null);
        }
        PermissionState candidate = change.apply().apply(policy.runtime());
        if (actor.mayLoosen() || !loosens(policy.matrix(), policy.matrixFor(candidate))) {
            apply(change, actor, null, null);
            return new Outcome(Status.APPLIED, change.target() + ": " + change.before() + " -> " + change.after()
                    + ".", null);
        }
        ObjectNode arguments = change.arguments().deepCopy();
        arguments.put("actor", actor.name());
        arguments.put("channel", actor.channel());
        try {
            Optional<String> id = approvals.requestOwnerAction(PermissionChangeAction.NAME,
                    JSON.writeValueAsString(arguments), change.preview(), origin);
            return id.map(requestId -> new Outcome(Status.PENDING, change.preview().replaceFirst("^Permission change: ",
                            "") + " loosens the policy, so it needs the owner's approval", requestId))
                    .orElseGet(() -> rejected("too many requests are already waiting for the owner"));
        } catch (JsonProcessingException e) {
            return rejected("the change could not be recorded");
        }
    }

    private void apply(Change change, Actor actor, String approvedBy, String requestId) {
        PermissionState next = change.apply().apply(policy.runtime());
        file.save(next);
        policy.install(next);
        Log.infof("Permission change by %s via %s: %s %s -> %s", actor.name(), actor.channel(), change.target(),
                change.before(), change.after());
        try {
            audit.append(new PermissionAuditEntry(clock.instant(), actor.name(), actor.channel(), change.target(),
                    change.before(), change.after(), approvedBy, requestId));
        } catch (RuntimeException e) {
            Log.errorf(e, "The permission change %s was applied but could not be written to the audit log",
                    change.target());
        }
    }

    private Change rebuild(JsonNode arguments) {
        String kind = arguments.path("kind").asText();
        PermissionLevel wanted = PermissionLevel.parse(arguments.path("level").asText(null)).orElse(null);
        String from = arguments.path("from").asText(null);
        switch (kind) {
            case "cell" -> {
                AccessClass operation = PermissionConfigReader.parseOperation(arguments.path("operation").asText(null))
                        .orElseThrow(() -> new IllegalStateException("the stored change names an unknown operation"));
                String group = arguments.path("group").asText();
                boolean defaultRow = DEFAULT_GROUP.equals(group);
                requireLevel(wanted);
                return new Change(kind, group + "." + name(operation),
                        state -> defaultRow ? state.withDefault(operation, wanted)
                                : state.withCell(group, operation, wanted),
                        from, wanted.label(), "Permission change: " + group + "." + name(operation) + ": " + from
                                + " -> " + wanted.label(), null);
            }
            case "tool" -> {
                String tool = arguments.path("tool").asText();
                requireLevel(wanted);
                return new Change(kind, "tool:" + tool, state -> state.withTool(tool, wanted), from, wanted.label(),
                        "Permission change: tool " + tool + ": " + from + " -> " + wanted.label(), null);
            }
            case "preset" -> {
                String preset = arguments.path("preset").asText();
                Map<AccessClass, PermissionLevel> levels = PermissionPresets.find(preset).orElseThrow(() ->
                        new IllegalStateException("the stored change names an unknown preset"));
                return new Change(kind, "preset", state -> state.withPreset(preset, levels), describe(policy.runtime()),
                        preset, "Permission change: apply the " + preset + " preset", null);
            }
            case "reset" -> {
                return new Change(kind, "reset", PermissionState::cleared, describe(policy.runtime()), "defaults",
                        "Permission change: reset every runtime change", null);
            }
            default -> throw new IllegalStateException("the stored change is of an unknown kind");
        }
    }

    private String currentLabel(JsonNode arguments) {
        if ("tool".equals(arguments.path("kind").asText())) {
            return policy.levelOf(arguments.path("tool").asText()).label();
        }
        String group = arguments.path("group").asText();
        AccessClass operation = PermissionConfigReader.parseOperation(arguments.path("operation").asText())
                .orElseThrow(() -> new IllegalStateException("the stored change names an unknown operation"));
        return (DEFAULT_GROUP.equals(group) ? policy.matrix().operationDefault(operation)
                : policy.matrix().cell(group, operation)).level().label();
    }

    private static void requireLevel(PermissionLevel level) {
        if (level == null) {
            throw new IllegalStateException("the stored change names an unknown level");
        }
    }

    private static boolean loosens(PermissionMatrix before, PermissionMatrix after) {
        for (String group : after.groups()) {
            for (AccessClass operation : AccessClass.values()) {
                if (before.cell(group, operation).level().isStricterThan(after.cell(group, operation).level())) {
                    return true;
                }
            }
        }
        for (AccessClass operation : AccessClass.values()) {
            if (before.operationDefault(operation).level().isStricterThan(after.operationDefault(operation).level())) {
                return true;
            }
        }
        return after.tools().stream().anyMatch(tool ->
                before.resolve(tool).level().isStricterThan(after.resolve(tool).level()));
    }

    private static String describe(PermissionState state) {
        return state.preset() != null ? state.preset() : state.isEmpty() ? "defaults" : "custom";
    }

    private static String name(AccessClass operation) {
        return operation.name().toLowerCase(Locale.ROOT);
    }

    private static Outcome rejected(String message) {
        return new Outcome(Status.REJECTED, message, null);
    }
}
