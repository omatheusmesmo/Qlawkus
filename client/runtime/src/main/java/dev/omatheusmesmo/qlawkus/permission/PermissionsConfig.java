package dev.omatheusmesmo.qlawkus.permission;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.Map;
import java.util.Optional;

/**
 * The starting point of the tool permission policy. Every tool has an access class (read, create,
 * update, delete, send or execute) and belongs to a group (calendar, drive, shell, ...); this says what
 * the agent may do with each, as {@code allow}, {@code ask} or {@code deny}. With nothing set every tool
 * is allowed, as it was before permissions existed. What the owner changes at runtime (the console, the
 * REST API or the {@code /permissions} chat command) is stored separately and takes precedence over
 * these values at the same specificity.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.permissions")
public interface PermissionsConfig {

    /**
     * A preset that fills the default of every operation: {@code readonly} (read only), {@code ask-writes}
     * (read and create freely, ask before update, delete, send and execute) or {@code trusted} (allow
     * everything). Operation defaults set explicitly below override the preset.
     */
    Optional<String> preset();

    /**
     * The level for an operation in any group, keyed by operation: {@code read}, {@code create},
     * {@code update}, {@code delete}, {@code send} or {@code execute}. The value is {@code allow},
     * {@code ask} or {@code deny}.
     */
    @WithName("default")
    Map<String, PermissionLevel> defaults();

    /**
     * The level for one operation of one group, keyed by group and then operation, for example
     * {@code qlawkus.permissions.group.calendar.delete=deny}. It wins over the operation default.
     */
    Map<String, Map<String, PermissionLevel>> group();

    /**
     * The level for one tool by the name the model sees, for example
     * {@code qlawkus.permissions.tool.deleteEvent=ask}. It wins over the group and the operation.
     */
    Map<String, PermissionLevel> tool();

    /**
     * How many changes the audit log keeps. The oldest are dropped first.
     */
    @WithDefault("500")
    int auditRetention();
}
