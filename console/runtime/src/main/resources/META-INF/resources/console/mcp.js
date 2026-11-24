/* MCP management uses the same authenticated admin APIs as agent tools. Never render remote HTML. */
(function () {
  "use strict";
  const byId = (id) => document.getElementById(id);
  const endpoint = "/api/admin/mcp/connections";
  const form = byId("mcp-form");
  if (!form) return;
  let busy = false;

  function feedback(text, error) {
    const node = byId("mcp-feedback");
    node.className = "result-msg " + (error ? "result-err" : "result-ok");
    node.setAttribute("role", error ? "alert" : "status");
    node.textContent = text;
  }

  async function request(url, method, body) {
    const response = await fetch(url, {
      method: method || "GET", credentials: "same-origin", mode: "same-origin", redirect: "error",
      cache: "no-store", signal: AbortSignal.timeout(120000),
      headers: body === undefined ? {} : {"Content-Type": "application/json"},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!response.ok) throw new Error("Request failed (" + response.status + "). Check the connection settings and vault references.");
    return response.status === 204 ? null : response.json();
  }

  async function action(work) {
    if (busy) return;
    busy = true;
    document.querySelectorAll(".mcp-actions button, #mcp-form button, #mcp-secret-form button, #mcp-refresh")
      .forEach((button) => { button.disabled = true; });
    try { await work(); }
    catch (error) { feedback(error.name === "TimeoutError" ? "The operation timed out. Refresh to check its status." : error.message, true); }
    finally {
      busy = false;
      document.querySelectorAll(".mcp-actions button, #mcp-form button, #mcp-secret-form button, #mcp-refresh")
        .forEach((button) => { button.disabled = false; });
    }
  }

  function jsonField(id, array) {
    const raw = byId(id).value.trim();
    let value;
    try { value = raw ? JSON.parse(raw) : (array ? [] : {}); }
    catch (ignored) { throw new Error(array ? "Command arguments must be a JSON array." : "Credential references must be a JSON object."); }
    if (array ? !Array.isArray(value) || value.some((part) => typeof part !== "string")
        : value === null || Array.isArray(value) || typeof value !== "object" || Object.values(value).some((part) => typeof part !== "string")) {
      throw new Error(array ? "Use an array of command argument strings." : "Use an object mapping names to vault alias strings.");
    }
    return value;
  }

  function transportFields() {
    const http = byId("mcp-transport").value === "HTTP";
    byId("mcp-http-fields").hidden = !http;
    byId("mcp-stdio-fields").hidden = http;
    byId("mcp-url").required = http;
    byId("mcp-command").required = !http;
    byId("mcp-oauth-fields").hidden = !http || byId("mcp-auth-kind").value === "NONE";
    for (const id of ["mcp-http-fields", "mcp-stdio-fields", "mcp-oauth-fields"]) {
      const group = byId(id);
      group.querySelectorAll("input, textarea, select").forEach((input) => { input.disabled = group.hidden; });
    }
  }

  function definition() {
    const http = byId("mcp-transport").value === "HTTP";
    const grant = byId("mcp-auth-kind").value;
    const oauth = !http || grant === "NONE" ? null : {
      grant, tokenEndpoint: byId("mcp-token-endpoint").value.trim(),
      authorizationEndpoint: byId("mcp-authorization-endpoint").value.trim() || null,
      revocationEndpoint: byId("mcp-revocation-endpoint").value.trim() || null,
      clientId: byId("mcp-client-id").value.trim(), clientSecretRef: byId("mcp-client-secret-ref").value.trim() || null,
      scopes: byId("mcp-scopes").value.trim().split(/\s+/).filter(Boolean),
      redirectUri: byId("mcp-redirect-uri").value.trim() || null,
      resource: byId("mcp-resource").value.trim() || null,
      clientAuthMethod: byId("mcp-client-auth-method").value
    };
    return {
      id: byId("mcp-id").value.trim(), transport: byId("mcp-transport").value,
      enabled: byId("mcp-enabled").checked, url: http ? byId("mcp-url").value.trim() : null,
      command: http ? [] : jsonField("mcp-command", true),
      headerSecretRefs: http ? jsonField("mcp-header-refs", false) : {},
      environmentSecretRefs: http ? {} : jsonField("mcp-env-refs", false),
      protocolVersion: byId("mcp-protocol").value.trim() || null, oauth
    };
  }

  function edit(connection) {
    byId("mcp-id").value = connection.id;
    byId("mcp-id").readOnly = true;
    byId("mcp-editor-title").textContent = "Edit connection · " + connection.id;
    byId("mcp-transport").value = connection.transport;
    byId("mcp-enabled").checked = connection.enabled;
    byId("mcp-url").value = connection.url || "";
    byId("mcp-command").value = JSON.stringify(connection.command || [], null, 2);
    byId("mcp-header-refs").value = JSON.stringify(connection.headerSecretRefs || {}, null, 2);
    byId("mcp-env-refs").value = JSON.stringify(connection.environmentSecretRefs || {}, null, 2);
    byId("mcp-protocol").value = connection.protocolVersion || "";
    const oauth = connection.oauth || {};
    byId("mcp-auth-kind").value = oauth.grant || "NONE";
    byId("mcp-client-auth-method").value = oauth.clientAuthMethod || "POST";
    for (const [id, value] of Object.entries({"token-endpoint": oauth.tokenEndpoint,
      "authorization-endpoint": oauth.authorizationEndpoint, "revocation-endpoint": oauth.revocationEndpoint,
      "client-id": oauth.clientId, "client-secret-ref": oauth.clientSecretRef, "redirect-uri": oauth.redirectUri,
      "resource": oauth.resource, "scopes": (oauth.scopes || []).join(" ")})) {
      byId("mcp-" + id).value = value || "";
    }
    byId("mcp-secret-id").value = connection.id;
    transportFields();
    form.scrollIntoView({behavior: "smooth", block: "start"});
    byId("mcp-transport").focus();
  }

  function button(text, work, variant) {
    const node = document.createElement("button");
    node.type = "button";
    node.className = "btn btn-" + (variant || "secondary") + " btn-sm";
    node.textContent = text;
    node.addEventListener("click", () => action(work));
    return node;
  }

  async function load() {
    const container = byId("mcp-connections");
    container.setAttribute("aria-busy", "true");
    try {
      const connections = await request(endpoint);
      if (!Array.isArray(connections)) throw new Error("The connection list could not be loaded.");
      container.replaceChildren();
      if (!connections.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "No connections yet. Add a local server or a remote endpoint below.";
        container.append(empty);
      }
      for (const connection of connections) {
        const row = document.createElement("article");
        row.className = "mcp-connection";
        const title = document.createElement("h3");
        title.textContent = connection.id;
        const detail = document.createElement("p");
        detail.className = "muted";
        detail.textContent = connection.transport + " · " + (connection.enabled ? "Enabled" : "Disabled");
        const actions = document.createElement("div");
        actions.className = "mcp-actions";
        const path = endpoint + "/" + encodeURIComponent(connection.id);
        actions.append(button("Edit", () => edit(connection)), button("Test", async () => {
          const status = await request(path + "/test", "POST");
          feedback(connection.id + ": " + status.state + " · " + status.toolCount + " tools", status.state === "UNAVAILABLE");
        }), button(connection.enabled ? "Disable" : "Enable", async () => {
          const status = await request(path, "PUT", {...connection, enabled: !connection.enabled});
          await load(); feedback(connection.id + ": " + status.state, false);
        }));
        if (connection.oauth && connection.oauth.grant === "AUTHORIZATION_CODE") {
          actions.append(button("Connect / renew login", async () => {
            const login = await request(path + "/auth/begin", "POST");
            const url = new URL(login.authorizationUrl);
            if (!["http:", "https:"].includes(url.protocol)) throw new Error("The authorization URL is invalid.");
            feedback("Continue in your identity provider to authorize " + connection.id + ".", false);
            const link = document.createElement("a");
            link.href = url.href; link.target = "_blank"; link.rel = "noopener noreferrer";
            link.textContent = "Open secure sign-in";
            byId("mcp-feedback").append(document.createTextNode(" "), link);
          }), button("Disconnect login", async () => {
            if (!await window.qlawkusConfirm({action: "Disconnect login", target: connection.id,
              message: "The stored access is removed and the provider is asked to revoke it. The connection needs a new sign-in."})) return;
            const result = await request(path + "/auth", "DELETE");
            feedback(result.remoteRevoked ? "Access disconnected and revoked." : "Local access disconnected. Provider revocation was unavailable.", false);
          }));
        }
        const remove = button("Remove", async () => {
          if (!await window.qlawkusConfirm({action: "Remove connection", target: connection.id,
            message: "The connection and its tools are removed from the agent, and any sign-in stored for it is forgotten."})) return;
          await request(path, "DELETE"); await load(); feedback("Connection removed.", false);
        }, "danger");
        actions.append(remove); row.append(title, detail, actions); container.append(row);
      }
    } finally { container.setAttribute("aria-busy", "false"); }
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    action(async () => {
      const connection = definition();
      const result = await request(endpoint + "/" + encodeURIComponent(connection.id), "PUT", connection);
      await load(); feedback(connection.id + " saved · " + result.state, false);
    });
  });
  byId("mcp-secret-form").addEventListener("submit", (event) => {
    event.preventDefault();
    action(async () => {
      const id = byId("mcp-secret-id").value.trim();
      const kind = byId("mcp-secret-kind").value;
      const entered = byId("mcp-secret-name").value.trim();
      const name = kind === "headers" ? entered.toLowerCase() : kind === "oauth" ? "client-secret" : entered;
      if (!/^[a-z][a-z0-9-]{0,19}$/.test(id) || !/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(name)) throw new Error("Use a valid connection ID and credential name.");
      const alias = "qlawkus.mcp.servers." + id + "." + kind + "." + name;
      try { await request("/api/admin/secrets", "PUT", {alias, value: byId("mcp-secret-value").value}); }
      finally { byId("mcp-secret-value").value = ""; }
      if (byId("mcp-id").value.trim() === id) {
        if (kind === "oauth") byId("mcp-client-secret-ref").value = alias;
        else {
          const field = kind === "headers" ? "mcp-header-refs" : "mcp-env-refs";
          const refs = jsonField(field, false); refs[entered] = alias; byId(field).value = JSON.stringify(refs, null, 2);
        }
      }
      feedback("Credential stored as " + alias + ". Save the connection to apply it.", false);
    });
  });
  byId("mcp-new").addEventListener("click", () => {
    form.reset(); byId("mcp-id").readOnly = false; byId("mcp-editor-title").textContent = "Add a connection";
    transportFields(); byId("mcp-id").focus();
  });
  byId("mcp-refresh").addEventListener("click", () => action(load));
  byId("mcp-transport").addEventListener("change", transportFields);
  byId("mcp-auth-kind").addEventListener("change", transportFields);
  byId("mcp-secret-kind").addEventListener("change", () => {
    const oauth = byId("mcp-secret-kind").value === "oauth";
    byId("mcp-secret-name").readOnly = oauth;
    if (oauth) byId("mcp-secret-name").value = "client-secret";
  });
  transportFields(); action(load);
})();
