package dev.omatheusmesmo.qlawkus.specialists;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.regex.Pattern;

@RegisterForReflection
public record SpecialistDefinition(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = ID_FORMAT) String id,
        @NotBlank @Size(max = 1_200) String instructions,
        @NotNull SpecialistsConfig.Tier model, boolean enabled) {

    private static final String ID_FORMAT = "[a-z][a-z0-9-]{0,31}";
    private static final Pattern VALID_ID = Pattern.compile(ID_FORMAT);

    public SpecialistDefinition {
        if (instructions != null) {
            instructions = instructions.strip();
        }
    }

    static boolean validId(String value) {
        return value != null && VALID_ID.matcher(value).matches();
    }
}
