package dev.omatheusmesmo.qlawkus.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The console and the docs site share one visual system. Each stylesheet carries the same token block between
 * two markers, so a change made to one that is not made to the other, or a text colour that stops being
 * readable, fails here instead of reaching a screenshot.
 */
class DesignTokensTest {

    private static final Path CONSOLE_CSS = Path.of("src/main/resources/META-INF/resources/console/app.css");
    private static final Path CONSOLE_FONTS = Path.of("src/main/resources/META-INF/resources/console/fonts");
    private static final Path SITE_CSS = Path.of("../../site/web/app.css");
    private static final Path SITE_FONTS = Path.of("../../site/web/fonts");
    private static final Path SITE_LAYOUT = Path.of("../../site/templates/layouts/default.html");

    private static final String START = "/* qlawkus-tokens:start";
    private static final String END = "/* qlawkus-tokens:end";
    private static final Pattern DECLARATION = Pattern.compile("(--[a-z0-9-]+)\\s*:\\s*([^;]+);");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final double MINIMUM_CONTRAST = 4.5;

    private static final List<String[]> TEXT_PAIRS = List.of(
            new String[] {"--text", "--bg"}, new String[] {"--text", "--surface"},
            new String[] {"--muted", "--bg"}, new String[] {"--muted", "--surface"},
            new String[] {"--faint", "--bg"}, new String[] {"--faint", "--surface"},
            new String[] {"--accent-ink", "--bg"}, new String[] {"--accent-ink", "--surface"},
            new String[] {"--danger", "--surface"}, new String[] {"--up", "--surface"},
            new String[] {"--speak", "--surface"}, new String[] {"--warn-ink", "--surface"},
            new String[] {"--note", "--surface"}, new String[] {"--code-text", "--code-bg"},
            new String[] {"#ffffff", "--accent-strong"}, new String[] {"#ffffff", "--accent-strong-hover"});

    @Test
    void theConsoleAndTheSiteCarryTheSameTokenBlock() throws IOException {
        assertEquals(tokenBlock(CONSOLE_CSS), tokenBlock(SITE_CSS),
                "Copy the token block between the qlawkus-tokens markers to both stylesheets.");
    }

    @Test
    void everyDarkTokenHasALightCounterpartAndBothDarkSelectorsAgree() throws IOException {
        String block = tokenBlock(CONSOLE_CSS);
        Map<String, String> light = declarations(rule(block, ":root {"));
        Map<String, String> byPreference = declarations(rule(block, ":root:not([data-theme=\"light\"]) {"));
        Map<String, String> byChoice = declarations(rule(block, ":root[data-theme=\"dark\"] {"));

        assertEquals(byChoice, byPreference, "The system dark rule and the chosen dark rule must match.");
        assertTrue(light.keySet().containsAll(byChoice.keySet()), "A dark token has no light value.");
    }

    @Test
    void textColoursAreReadableInBothThemes() throws IOException {
        String block = tokenBlock(CONSOLE_CSS);
        Map<String, String> light = declarations(rule(block, ":root {"));
        Map<String, String> dark = new LinkedHashMap<>(light);
        dark.putAll(declarations(rule(block, ":root[data-theme=\"dark\"] {")));

        for (var theme : Map.of("light", light, "dark", dark).entrySet()) {
            for (String[] pair : TEXT_PAIRS) {
                double ratio = contrast(color(theme.getValue(), pair[0]), color(theme.getValue(), pair[1]));
                assertTrue(ratio >= MINIMUM_CONTRAST, "%s theme: %s on %s is %.2f:1, below %.1f:1"
                        .formatted(theme.getKey(), pair[0], pair[1], ratio, MINIMUM_CONTRAST));
            }
        }
    }

    @Test
    void neitherSurfaceLoadsAFontOrStyleFromAThirdParty() throws IOException {
        for (Path css : List.of(CONSOLE_CSS, SITE_CSS)) {
            String text = Files.readString(css);
            assertFalse(text.contains("@import"), css + " imports a stylesheet.");
            assertFalse(Pattern.compile("url\\(\\s*[\"']?https?:").matcher(text).find(), css + " loads a remote url.");
        }
        String layout = Files.readString(SITE_LAYOUT);
        assertFalse(layout.contains("fonts.googleapis.com") || layout.contains("fonts.gstatic.com"),
                "The site layout still requests Google Fonts.");
    }

    @Test
    void theSelfHostedFontShipsWithBothSurfaces() throws IOException {
        for (Path fonts : List.of(CONSOLE_FONTS, SITE_FONTS)) {
            for (String subset : List.of("manrope-latin.woff2", "manrope-latin-ext.woff2")) {
                Path file = fonts.resolve(subset);
                assertTrue(Files.isRegularFile(file) && Files.size(file) > 10_000, "Missing font " + file);
            }
        }
        for (Path css : List.of(CONSOLE_CSS, SITE_CSS)) {
            String text = Files.readString(css);
            assertTrue(text.contains("font-family: \"Manrope\"") && text.contains("fonts/manrope-latin.woff2"),
                    css + " does not declare the self-hosted Manrope.");
        }
    }

    private static String tokenBlock(Path css) throws IOException {
        String text = Files.readString(css);
        int start = text.indexOf(START);
        int end = text.indexOf(END);
        assertTrue(start >= 0 && end > start, css + " has no token block.");
        return text.substring(text.indexOf('\n', start) + 1, end).strip();
    }

    private static String rule(String block, String selector) {
        int open = block.indexOf(selector);
        assertTrue(open >= 0, "Token block has no rule " + selector);
        int from = open + selector.length();
        return block.substring(from, block.indexOf('}', from));
    }

    private static Map<String, String> declarations(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = DECLARATION.matcher(body);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2).strip());
        }
        return values;
    }

    private static int[] color(Map<String, String> tokens, String reference) {
        String value = reference.startsWith("#") ? reference : tokens.get(reference);
        assertTrue(value != null && HEX.matcher(value).matches(), reference + " is not a hex colour: " + value);
        return new int[] {Integer.parseInt(value.substring(1, 3), 16), Integer.parseInt(value.substring(3, 5), 16),
                Integer.parseInt(value.substring(5, 7), 16)};
    }

    private static double contrast(int[] first, int[] second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int[] rgb) {
        double[] channel = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = rgb[i] / 255.0;
            channel[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel[0] + 0.7152 * channel[1] + 0.0722 * channel[2];
    }
}
