/* Install panel for the skill hub. The hub answers with JSON, and skill names, descriptions, sources and reasons
   are text the owner or a registry wrote, so every reply is rendered as text nodes and never as markup. The
   hub's endpoints are the only install path: this script only collects what the owner typed or chose and
   reports what the hub answered. */
(function () {
  "use strict";

  var panel = document.getElementById("skill-install");
  if (!panel) {
    return;
  }
  var maxInstalls = Number(panel.getAttribute("data-max-installs")) || 0;
  var uploadLimit = Number(panel.getAttribute("data-upload-limit")) || 0;
  var result = document.getElementById("install-result");
  var candidates = document.getElementById("skill-candidates");
  var candidateList = document.getElementById("candidate-list");
  var candidateForm = document.getElementById("candidate-form");
  var linkForm = document.getElementById("install-link-form");
  var textForm = document.getElementById("install-text-form");
  var uploadForm = document.getElementById("install-upload-form");
  var pending = null;

  var HEADINGS = {
    limit: "Over a cap",
    unreachable: "Source unreachable",
    refused: "Refused",
    disabled: "Skill hub disabled"
  };

  function show(cssClass, text) {
    var message = document.createElement("div");
    message.className = "result-msg " + cssClass;
    message.setAttribute("role", cssClass === "result-err" ? "alert" : "status");
    message.textContent = text;
    result.replaceChildren(message);
  }

  function failureText(status, body) {
    var kind = body && body.kind ? body.kind : "refused";
    var reason = body && body.error ? body.error : "the request failed with status " + status + ".";
    return (HEADINGS[kind] || HEADINGS.refused) + ": " + reason;
  }

  function send(method, path, headers, body) {
    return fetch(path, {
      method: method,
      headers: headers,
      body: body,
      credentials: "same-origin"
    }).then(function (response) {
      return response.text().then(function (text) {
        var parsed = null;
        try {
          parsed = text ? JSON.parse(text) : null;
        } catch (ignored) {
          parsed = null;
        }
        return { ok: response.ok, status: response.status, body: parsed };
      });
    });
  }

  function setBusy(form, busy) {
    var controls = form.querySelectorAll("button, input, textarea");
    for (var i = 0; i < controls.length; i++) {
      controls[i].disabled = busy;
    }
  }

  function run(form, workingText, request, onInstalled) {
    setBusy(form, true);
    show("result-busy", workingText);
    request().then(function (reply) {
      setBusy(form, false);
      if (reply.ok) {
        onInstalled(reply.body);
      } else {
        show("result-err", failureText(reply.status, reply.body));
      }
    }, function (error) {
      setBusy(form, false);
      show("result-err", "Request failed: " + (error && error.message ? error.message : "no response."));
    });
  }

  function postJson(path, payload) {
    return send("POST", path, { "Content-Type": "application/json" }, JSON.stringify(payload));
  }

  function postUpload(path, file, skills) {
    var data = new FormData();
    data.append("file", file, file.name);
    if (skills !== null) {
      data.append("skills", skills);
    }
    return send("POST", path, {}, data);
  }

  function checkedNames() {
    var boxes = candidateList.querySelectorAll("input[type=checkbox]");
    var names = [];
    for (var i = 0; i < boxes.length; i++) {
      if (boxes[i].checked) {
        names.push(boxes[i].value);
      }
    }
    return names;
  }

  function showCandidates(body) {
    var skills = (body && body.skills) || [];
    candidateList.replaceChildren();
    if (skills.length === 0) {
      candidates.hidden = true;
      show("result-err", "Refused: nothing installable was found in that source.");
      return;
    }
    skills.forEach(function (skill) {
      var item = document.createElement("li");
      var label = document.createElement("label");
      label.className = "check";
      var box = document.createElement("input");
      box.type = "checkbox";
      box.value = skill.name;
      box.checked = true;
      var name = document.createElement("span");
      name.className = "check-label mono";
      name.textContent = skill.name;
      label.append(box, name);
      item.append(label);
      if (skill.location) {
        var where = document.createElement("span");
        where.className = "muted mono";
        where.textContent = skill.location;
        item.append(where);
      }
      candidateList.append(item);
    });
    candidates.hidden = false;
    show("result-ok", "Found " + skills.length + " skill(s). Choose which to install.");
  }

  function showInstalled(body) {
    var names = (body && body.installed) || [];
    candidates.hidden = true;
    candidateList.replaceChildren();
    pending = null;
    show("result-ok", "Installed: " + names.join(", ") + ".");
    refreshInstalled();
  }

  function refreshInstalled() {
    fetch(window.location.href, { credentials: "same-origin" })
      .then(function (response) {
        return response.text();
      })
      .then(function (html) {
        var fresh = new DOMParser().parseFromString(html, "text/html").getElementById("installed-skills");
        var current = document.getElementById("installed-skills");
        if (fresh && current) {
          current.replaceWith(fresh);
          if (window.htmx) {
            window.htmx.process(fresh);
          }
        }
      })
      .catch(function () {
      });
  }

  linkForm.addEventListener("submit", function (event) {
    event.preventDefault();
    var source = document.getElementById("install-source").value.trim();
    if (!source) {
      show("result-err", "Refused: paste a link or an owner/repo slug.");
      return;
    }
    pending = { source: source, file: null };
    run(linkForm, "Looking inside " + source + "...", function () {
      return postJson("/api/admin/skills/install/list", { source: source });
    }, showCandidates);
  });

  textForm.addEventListener("submit", function (event) {
    event.preventDefault();
    var text = document.getElementById("install-text").value;
    if (!text.trim()) {
      show("result-err", "Refused: paste the SKILL.md text to install.");
      return;
    }
    run(textForm, "Installing the pasted skill...", function () {
      return send("POST", "/api/admin/skills/install/text", { "Content-Type": "text/plain" }, text);
    }, showInstalled);
  });

  uploadForm.addEventListener("submit", function (event) {
    event.preventDefault();
    var file = document.getElementById("install-file").files[0];
    if (!file) {
      show("result-err", "Refused: choose a SKILL.md or an archive to upload.");
      return;
    }
    if (uploadLimit && file.size > uploadLimit) {
      show("result-err", "Over a cap: " + file.name + " is " + file.size + " bytes; the upload limit is "
        + uploadLimit + " bytes.");
      return;
    }
    pending = { source: null, file: file };
    run(uploadForm, "Reading " + file.name + "...", function () {
      return postUpload("/api/admin/skills/install/upload/list", file, null);
    }, showCandidates);
  });

  candidateForm.addEventListener("submit", function (event) {
    event.preventDefault();
    if (!pending) {
      show("result-err", "Refused: find the skills in a source first.");
      return;
    }
    var chosen = checkedNames();
    if (chosen.length === 0) {
      show("result-err", "Refused: choose at least one skill to install.");
      return;
    }
    if (maxInstalls && chosen.length > maxInstalls) {
      show("result-err", "Over a cap: " + chosen.length + " skills selected, but at most " + maxInstalls
        + " are installed per call.");
      return;
    }
    var skills = chosen.join(",");
    var source = pending;
    run(candidateForm, "Installing " + skills + "...", function () {
      if (source.file) {
        return postUpload("/api/admin/skills/install/upload/selection", source.file, skills);
      }
      return postJson("/api/admin/skills/install/selection", { source: source.source, skills: skills });
    }, showInstalled);
  });

  document.addEventListener("htmx:afterRequest", function (event) {
    var element = event.detail.elt;
    if (event.detail.successful && element && element.hasAttribute && element.hasAttribute("data-remove-row")) {
      var row = element.closest("tr");
      if (row) {
        row.remove();
      }
    }
  });
})();
