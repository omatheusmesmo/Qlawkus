package dev.omatheusmesmo.qlawkus.testing;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class QlawkusTestUtils {

    private static final String MOCK_ANSWER = "QlawkusMock";

    private static volatile Boolean mocked;

    private static boolean detectMock() {
        try {
            boolean explicit = ConfigProvider.getConfig()
                    .getOptionalValue("qlawkus.test.mock-mode", Boolean.class)
                    .orElse(false);
            if (explicit) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            String baseUrl = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.langchain4j.openai.\"primary\".base-url", String.class)
                    .orElse("");
            if (baseUrl.contains("wiremock") || baseUrl.contains("mock") || baseUrl.contains("localhost")) {
                return true;
            }
        } catch (Exception ignored) {
        }

        String apiKey = System.getenv("LLM_API_KEY");
        return apiKey == null || apiKey.isBlank() || "dummy".equals(apiKey);
    }

    private static boolean useMock() {
        if (mocked == null) {
            mocked = detectMock();
        }
        return mocked;
    }

    public static boolean usesLLM() {
        return !useMock();
    }

    public static boolean isMock() {
        return useMock();
    }

    public static Matcher<String> containsStringOrMock(String... expected) {
        if (useMock()) {
            return Matchers.containsString(MOCK_ANSWER);
        } else {
            List<Matcher<? super String>> possibilities = new ArrayList<>(expected.length);
            for (String value : expected) {
                possibilities.add(Matchers.containsStringIgnoringCase(value));
            }
            return Matchers.anyOf(possibilities);
        }
    }

    public static void assertResponseContainsOrMock(String response, String... expected) {
        if (useMock()) {
            if (response == null || !response.contains(MOCK_ANSWER)) {
                throw new AssertionError(
                        "In mock mode, response should contain '" + MOCK_ANSWER + "'. Got: " + response);
            }
        } else {
            String lower = response.toLowerCase();
            for (String e : expected) {
                if (lower.contains(e.toLowerCase())) {
                    return;
                }
            }
            throw new AssertionError(
                    "Response should contain one of [" + String.join(", ", expected)
                            + "]. Got: " + response);
        }
    }

    public static void assertConditionOrMock(boolean condition, String message) {
        if (useMock()) {
            return;
        }
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertNotContainsOrMock(String response, String unexpected, String message) {
        if (useMock()) {
            return;
        }
        if (response.toLowerCase().contains(unexpected.toLowerCase())) {
            throw new AssertionError(message + ". Got: " + response);
        }
    }

    public static String mockResponse() {
        return MOCK_ANSWER;
    }

    static void resetMockState() {
        mocked = null;
    }

    private QlawkusTestUtils() {
    }
}
