(() => {
    "use strict";

    const api = "/api/admin/specialists";
    const catalog = document.getElementById("specialist-catalog");
    const status = document.getElementById("specialist-status");
    const form = document.getElementById("specialist-editor");
    const heading = document.getElementById("specialist-editor-heading");
    const id = document.getElementById("specialist-id");
    const instructions = document.getElementById("specialist-instructions");
    const model = document.getElementById("specialist-model");
    const enabled = document.getElementById("specialist-enabled");
    const save = document.getElementById("specialist-save");
    const reset = document.getElementById("specialist-new");
    const remove = document.getElementById("specialist-remove");
    const modelNames = {FALLBACK: "Local fallback", PRIMARY: "Primary", SECONDARY: "Secondary"};
    let availableModels = [];
    let current = null;
    let busy = false;

    async function request(path, method = "GET", body) {
        const options = {method, headers: {"Accept": "application/json"}};
        if (body !== undefined) {
            options.headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }
        const response = await fetch(api + path, options);
        if (!response.ok) {
            const problem = await response.json().catch(() => null);
            throw new Error(problem?.error || "The specialist request failed (" + response.status + ").");
        }
        return response.status === 204 ? null : response.json();
    }

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }

    function edit(specialist) {
        current = specialist?.id ?? null;
        heading.textContent = current ? "Edit " + current : "New specialist";
        id.value = current ?? "";
        id.readOnly = current !== null;
        instructions.value = specialist?.instructions ?? "";
        enabled.checked = specialist?.enabled ?? true;
        model.replaceChildren();
        for (const tier of availableModels) {
            model.add(new Option(modelNames[tier] ?? tier, tier));
        }
        if (specialist && !availableModels.includes(specialist.model)) {
            model.add(new Option((modelNames[specialist.model] ?? specialist.model) + " (restricted)", specialist.model));
        }
        if (specialist) model.value = specialist.model;
        reset.hidden = current === null;
        remove.hidden = current === null;
        save.disabled = busy || model.options.length === 0;
    }

    function render(specialists) {
        if (!specialists.length) {
            catalog.replaceChildren(element("p", "Specialists created by you or the agent appear here.", "muted"));
            return;
        }

        const table = element("table", undefined, "tbl tbl-stack");
        const headers = table.createTHead().insertRow();
        for (const title of ["Specialist", "Model", "Status", "Actions"]) {
            const cell = element("th", title);
            cell.scope = "col";
            headers.append(cell);
        }

        const rows = table.createTBody();
        for (const specialist of specialists) {
            const row = rows.insertRow();
            const description = row.insertCell();
            description.dataset.label = "Specialist";
            description.append(element("strong", specialist.id, "mono"),
                element("div", specialist.instructions, "specialist-instructions muted"));
            const tier = row.insertCell();
            tier.dataset.label = "Model";
            tier.textContent = modelNames[specialist.model] ?? specialist.model;
            const state = row.insertCell();
            state.dataset.label = "Status";
            const usable = specialist.enabled && availableModels.includes(specialist.model);
            const label = usable ? "Enabled" : specialist.enabled ? "Model restricted" : "Disabled";
            state.append(element("span", label, "pill " + (usable ? "pill-ok" : "pill-err")));
            const actions = row.insertCell();
            actions.dataset.label = "Actions";
            const button = element("button", "Edit", "btn btn-secondary btn-sm");
            button.type = "button";
            button.setAttribute("aria-label", "Edit " + specialist.id);
            button.addEventListener("click", () => {
                if (busy) return;
                edit(specialist);
                instructions.focus();
            });
            actions.append(button);
        }
        catalog.replaceChildren(table);
    }

    async function load(specialist) {
        const data = await request("");
        availableModels = data.models;
        render(data.specialists);
        edit(specialist);
        catalog.setAttribute("aria-busy", "false");
    }

    reset.addEventListener("click", () => {
        if (!busy) {
            edit(null);
            id.focus();
        }
    });

    remove.addEventListener("click", async () => {
        if (busy || !current || !window.confirm("Remove " + current + " from the specialist catalog?")) return;
        busy = true;
        save.disabled = true;
        const selected = current;
        try {
            await request("/" + encodeURIComponent(selected), "DELETE");
            await load(null);
            status.textContent = "Removed " + selected + ".";
        } catch (failure) {
            status.textContent = failure.message;
        } finally {
            busy = false;
            save.disabled = model.options.length === 0;
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (busy) return;
        busy = true;
        save.disabled = true;
        try {
            const body = {id: id.value, instructions: instructions.value, model: model.value, enabled: enabled.checked};
            if (current) {
                await request("/" + encodeURIComponent(current), "PUT", body);
            } else {
                await request("", "POST", body);
            }
            await load(body);
            status.textContent = "Saved " + body.id + ".";
        } catch (failure) {
            status.textContent = failure.message;
        } finally {
            busy = false;
            save.disabled = model.options.length === 0;
        }
    });

    load(null).catch((failure) => {
        catalog.setAttribute("aria-busy", "false");
        catalog.replaceChildren();
        status.textContent = failure.message;
    });
})();
