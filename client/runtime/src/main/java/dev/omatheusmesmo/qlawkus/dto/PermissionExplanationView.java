package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionService;
import java.util.List;

/**
 * The API view of why a tool is decided the way it is.
 */
public record PermissionExplanationView(String tool, String group, String accessClass, String level,
        List<String> reasons) {

    public static PermissionExplanationView of(PermissionService.Explanation explanation) {
        return new PermissionExplanationView(explanation.tool(), explanation.group(), explanation.accessClass().name(),
                explanation.level().label(), explanation.reasons());
    }
}
