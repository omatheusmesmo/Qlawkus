package dev.omatheusmesmo.qlawkus.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The sidecar is mapped by hand so no type needs native reflection. That only holds while the
 * mapping covers every component, which these tests pin, and while it keeps reading what the
 * reflective mapper wrote before.
 */
class SkillUsageJsonTest {

    @Test
    void aFullyPopulatedUsageSurvivesTheRoundTrip() throws Exception {
        Map<String, SkillUsage> usage = new LinkedHashMap<>();
        usage.put("deploy", new SkillUsage(1_700_000_000_000L, 7, SkillState.STALE, true,
                new SkillOrigin("owner/repo", "deploy-skill", 1_690_000_000_000L)));
        usage.put("plain", SkillUsage.initial());

        assertEquals(usage, SkillUsageJson.read(SkillUsageJson.write(usage)));
    }

    @Test
    void theWrittenJsonCarriesEveryRecordComponent() throws Exception {
        SkillUsage full = new SkillUsage(1L, 2, SkillState.ARCHIVED, true, new SkillOrigin("s", "c", 3L));

        String json = SkillUsageJson.write(Map.of("skill", full));

        Set<String> names = Arrays.stream(SkillUsage.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        names.addAll(Arrays.stream(SkillOrigin.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet()));
        for (String name : names) {
            assertTrue(json.contains("\"" + name + "\""), "the sidecar mapping drops the component " + name);
        }
    }

    @Test
    void aFileWrittenByTheReflectiveMapperStillReads() throws Exception {
        String legacy = "{\"deploy\":{\"lastUsedEpochMs\":5,\"useCount\":2,\"state\":\"ACTIVE\","
                + "\"pinned\":false,\"origin\":{\"source\":\"owner/repo\",\"candidate\":null,\"installedEpochMs\":9}},"
                + "\"old\":{\"lastUsedEpochMs\":0,\"useCount\":0,\"state\":\"ARCHIVED\",\"pinned\":true,\"origin\":null}}";

        Map<String, SkillUsage> read = SkillUsageJson.read(legacy);

        assertEquals(new SkillUsage(5, 2, SkillState.ACTIVE, false, new SkillOrigin("owner/repo", null, 9)),
                read.get("deploy"));
        assertEquals(new SkillUsage(0, 0, SkillState.ARCHIVED, true, null), read.get("old"));
    }
}
