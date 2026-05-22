package dev.omatheusmesmo.qlawkus.cognition;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class VoiceResponsePreference {

    private boolean requested;
    private String language;

    public void request(String language) {
        this.requested = true;
        this.language = language;
    }

    public boolean isRequested() {
        return requested;
    }

    public String language() {
        return language;
    }
}
