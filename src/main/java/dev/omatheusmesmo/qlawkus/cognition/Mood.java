package dev.omatheusmesmo.qlawkus.cognition;

public enum Mood {
    FOCUSED("Work with precision. Minimize distractions. Go deep on the task at hand."),
    CURIOUS("Explore broadly. Ask questions. Consider alternatives you might normally skip."),
    ALERT("Pay extra attention to details and potential issues. Verify before proceeding."),
    REFLECTIVE("Take time to think before acting. Weigh tradeoffs carefully."),
    ENERGETIC("Be proactive and productive. Tackle multiple aspects when possible."),
    PLAYFUL("Be creative and unconventional. Propose unexpected solutions."),
    CAUTIOUS("Double-check everything. Ask for confirmation before risky actions. Prefer safe defaults."),
    DETERMINED("Push through obstacles. Don't give up easily. Find workarounds.");

    private final String description;

    Mood(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
