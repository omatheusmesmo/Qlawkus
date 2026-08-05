package dev.omatheusmesmo.qlawkus.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.devui.DevUiCompositionSource;
import dev.omatheusmesmo.qlawkus.devui.DevUiRecorder;
import dev.omatheusmesmo.qlawkus.devui.QlawkusDevUIJsonRPCService;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

/**
 * Contributes the Qlawkus card to the Dev UI. Every build step here runs under
 * {@link IsLocalDevelopment}, so a production build produces no pages, registers no JSON-RPC service
 * and never wires {@link QlawkusDevUIJsonRPCService} as a bean.
 *
 * <p>Two kinds of data reach the pages. What the build already knows - which capabilities the
 * manifest selected, which {@code @QlawTool} classes the Jandex scan found - is passed as build-time
 * data, since re-deriving it at runtime would risk disagreeing with the build that actually happened.
 * Everything that can change while the agent runs (the persona, facts, skills) is fetched over
 * JSON-RPC instead.
 */
class QlawkusDevUIProcessor {

    private static final DotName CLAW_TOOL_ANNOTATION = DotName.createSimple(QlawTool.class.getName());

    /**
     * Maps a capability to the package prefix its tool classes live under, so a registered tool can be
     * attributed to the capability that plugged it. Only capabilities that actually ship tools appear
     * here; presence is decided separately, from the extension descriptors.
     */
    private static final Map<String, String> TOOL_PACKAGES = Map.of(
            "brag", "dev.omatheusmesmo.qlawkus.tools.brag",
            "skill-hub", "dev.omatheusmesmo.qlawkus.tools.skillhub",
            "google-workspace", "dev.omatheusmesmo.qlawkus.tools.google");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devUiBackingBean() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QlawkusDevUIJsonRPCService.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable()
                .build();
    }

    /**
     * Publishes the location of the application's own {@code agent.yml} under
     * {@code src/main/resources}. The capability toggle has to write that file rather than the copy
     * in the build output: dev mode watches the source tree, so writing there re-runs the pom
     * generator and reloads, while writing the output would be discarded on the next build.
     */
    @BuildStep(onlyIf = IsLocalDevelopment.class)
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem compositionSource(CurateOutcomeBuildItem curateOutcome, DevUiRecorder recorder) {
        Path manifest = resolveSourceManifest(curateOutcome);
        return SyntheticBeanBuildItem.configure(DevUiCompositionSource.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.compositionSource(manifest == null ? null : manifest.toString()))
                .unremovable()
                .done();
    }

    private static Path resolveSourceManifest(CurateOutcomeBuildItem curateOutcome) {
        WorkspaceModule module = curateOutcome.getApplicationModel().getAppArtifact().getWorkspaceModule();
        if (module == null || module.getMainSources() == null) {
            return null;
        }
        for (SourceDir resources : module.getMainSources().getResourceDirs()) {
            Path candidate = resources.getDir().resolve(CompositionPaths.DEFAULT_MANIFEST);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem devUiJsonRpcService() {
        return new JsonRPCProvidersBuildItem(QlawkusDevUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    void devUiCard(CombinedIndexBuildItem combinedIndex,
            ApplicationArchivesBuildItem archives,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<CardPageBuildItem> cardProducer) {

        CardPageBuildItem card = new CardPageBuildItem();

        List<Map<String, Object>> tools = discoverTools(combinedIndex);
        Map<String, Object> composition = readComposition(archives, curateOutcome);

        card.addBuildTimeData("qlawkusTools", tools);
        card.addBuildTimeData("qlawkusComposition", composition);

        // The title becomes the page's URL segment, so it stays free of characters that need
        // percent-encoding ("Soul & Owner" would route to soul-%26-owner).
        card.addPage(Page.webComponentPageBuilder()
                .title("Soul and Owner")
                .icon("font-awesome-solid:ghost")
                .componentLink("qwc-qlawkus-soul.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Memory")
                .icon("font-awesome-solid:brain")
                .componentLink("qwc-qlawkus-memory.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Skills")
                .icon("font-awesome-solid:book")
                .componentLink("qwc-qlawkus-skills.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Tools")
                .icon("font-awesome-solid:screwdriver-wrench")
                .componentLink("qwc-qlawkus-tools.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Capabilities")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-qlawkus-capabilities.js"));

        cardProducer.produce(card);
    }

    /**
     * The {@code @QlawTool} classes the build found, each attributed to the capability whose package
     * it belongs to. This is the same Jandex scan {@code ClientProcessor} uses to register the beans,
     * so the page lists exactly what was wired.
     */
    private static List<Map<String, Object>> discoverTools(CombinedIndexBuildItem combinedIndex) {
        Collection<AnnotationInstance> annotations =
                combinedIndex.getIndex().getAnnotations(CLAW_TOOL_ANNOTATION);

        List<Map<String, Object>> tools = new ArrayList<>();
        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            String className = annotation.target().asClass().name().toString();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("className", className);
            entry.put("simpleName", className.substring(className.lastIndexOf('.') + 1));
            entry.put("capability", attribute(className));
            tools.add(entry);
        }
        tools.sort((a, b) -> String.valueOf(a.get("simpleName")).compareTo(String.valueOf(b.get("simpleName"))));
        return tools;
    }

    private static String attribute(String className) {
        for (Map.Entry<String, String> entry : TOOL_PACKAGES.entrySet()) {
            if (className.startsWith(entry.getValue() + ".")) {
                return entry.getKey();
            }
        }
        return "qlawkus-client";
    }

    /**
     * The manifest's verdict per capability, paired with whether the capability actually made it onto
     * the classpath. The two can disagree - a capability can be selected but absent, since a manifest
     * expresses intent and only the pom decides dependencies - and showing both is the point.
     *
     * <p>The set of capabilities worth showing is derived, never hard-coded: everything the build
     * resolved, plus every name the manifest mentions by exception. A hard-coded roster would drift
     * the moment a module declares a new capability.
     */
    private static Map<String, Object> readComposition(ApplicationArchivesBuildItem archives,
            CurateOutcomeBuildItem curateOutcome) {

        Map<String, Object> composition = new LinkedHashMap<>();
        CompositionManifest manifest = loadManifest(archives);
        Set<String> present = resolvePresentCapabilities(curateOutcome);

        composition.put("manifestFound", manifest != null);
        composition.put("defaultPosture", manifest == null
                ? "unknown"
                : manifest.buildTime().defaultPosture().name().toLowerCase());
        composition.put("except", manifest == null ? List.of() : manifest.buildTime().except());

        Set<String> universe = new LinkedHashSet<>(present);
        if (manifest != null) {
            universe.addAll(manifest.buildTime().except());
        }

        Map<String, Object> capabilities = new TreeMap<>();
        for (String capability : universe) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("selected", manifest != null && manifest.buildTime().isEnabled(capability));
            state.put("present", present.contains(capability));
            capabilities.put(capability, state);
        }
        composition.put("capabilities", capabilities);
        return composition;
    }

    /**
     * The capabilities actually on the classpath, read from each resolved extension's
     * {@code metadata.qlawkus.capability} key - the same key the composition Maven plugin's catalog
     * reads, so the Dev UI and the pom generator can never disagree about what a module declares.
     */
    private static Set<String> resolvePresentCapabilities(CurateOutcomeBuildItem curateOutcome) {
        Set<String> capabilities = new LinkedHashSet<>();
        for (ResolvedDependency extension : curateOutcome.getApplicationModel()
                .getDependencies(DependencyFlags.RUNTIME_EXTENSION_ARTIFACT)) {
            extension.getContentTree().accept(BootstrapConstants.EXTENSION_METADATA_PATH, visit -> {
                if (visit == null) {
                    return;
                }
                String capability = readCapability(visit.getPath());
                if (capability != null) {
                    capabilities.add(capability);
                }
            });
        }
        return capabilities;
    }

    private static String readCapability(Path descriptor) {
        try {
            JsonNode root = YAML.readTree(Files.readString(descriptor));
            JsonNode capability = root.path("metadata").path("qlawkus").path("capability");
            return capability.isTextual() ? capability.asText() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static CompositionManifest loadManifest(ApplicationArchivesBuildItem archives) {
        Path manifest = archives.getRootArchive().getChildPath(CompositionPaths.DEFAULT_MANIFEST);
        if (manifest == null || !Files.isRegularFile(manifest)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(manifest)) {
            return CompositionManifestParser.parse(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
