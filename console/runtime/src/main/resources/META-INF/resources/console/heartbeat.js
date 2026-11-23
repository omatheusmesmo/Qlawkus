(() => {
    const panel = document.getElementById("heartbeat");
    if (!panel) return;

    const endpoint = "/api/admin/heartbeat";
    const form = panel.querySelector("form");
    const fields = form.querySelector("fieldset");
    const enabled = form.elements.namedItem("enabled");
    const interval = form.elements.namedItem("interval");
    const prompt = form.elements.namedItem("prompt");
    const provider = form.elements.namedItem("provider");
    const refreshButton = panel.querySelector("[data-heartbeat-refresh]");
    const result = panel.querySelector("[data-heartbeat-result]");
    const output = (id, value) => panel.querySelector('[data-heartbeat="' + id + '"]').textContent = value;
    const formatTime = value => value ? new Date(value).toLocaleString() : "—";

    function display(status) {
        const settings = status.settings;
        enabled.checked = settings.enabled;
        interval.min = status.minimumIntervalMinutes;
        interval.value = settings.intervalMinutes;
        prompt.value = settings.prompt;
        provider.replaceChildren(new Option("Silent observation", ""));
        for (const name of status.availableProviders) {
            provider.add(new Option(name, name));
        }
        if (settings.provider && !status.availableProviders.includes(settings.provider)) {
            provider.add(new Option(settings.provider + " (unavailable)", settings.provider));
        }
        provider.value = settings.provider || "";

        const last = status.lastRun;
        const localStatus = status.storageAvailable ? (settings.enabled ? "Enabled" : "Disabled") : "Storage unavailable";
        output("state", localStatus);
        output("next", formatTime(status.nextRun));
        output("last", last ? String(last.outcome).toLowerCase().replaceAll("_", " ") + " · "
            + formatTime(last.finishedAt || last.startedAt) : "—");
        output("destination", !settings.provider ? "Silent" : status.destinationAvailable
            ? settings.provider : "Unavailable · choose an accepted conversation and save");
        output("observations", last && last.taskIssues !== null
            ? last.taskIssues + " schedule issues · " + (last.hasCuratedMemory ? "curated memory present" : "no curated memory")
            : "—");
        output("budget", status.remainingDailyRuns + " of " + status.maximumDailyRuns
            + " calls left today · " + status.maximumDecisionTokens + " tokens per decision · "
            + status.deadlineSeconds + " second deadline");
    }

    async function request(options) {
        fields.disabled = true;
        refreshButton.disabled = true;
        result.textContent = "";
        let ready = false;
        try {
            const response = await fetch(endpoint, options);
            if (!response.ok) {
                ready = options !== undefined;
                const problem = await response.json().catch(() => ({}));
                result.textContent = problem.error || (options ? "Could not save heartbeat settings." : "Could not load heartbeat.");
                return;
            }
            display(await response.json());
            ready = true;
            if (options) result.textContent = "Heartbeat settings saved.";
        } catch (_) {
            ready = options !== undefined;
            result.textContent = options ? "Could not save heartbeat settings." : "Could not load heartbeat.";
        } finally {
            fields.disabled = !ready;
            refreshButton.disabled = false;
        }
    }

    form.addEventListener("submit", event => {
        event.preventDefault();
        request({
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                enabled: enabled.checked,
                intervalMinutes: Number(interval.value),
                prompt: prompt.value,
                provider: provider.value || null
            })
        });
    });
    refreshButton.addEventListener("click", () => request());
    request();
})();
