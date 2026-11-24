/* Search, filters and collapsible sections for the config editor. Everything here works on rows the
   server already rendered: the script only shows, hides and opens them, and never adds a value to the
   page. The state (search text, filters, opened sections) lives in the URL hash so a view can be shared. */
(function () {
  "use strict";

  var toolbar = document.getElementById("cfg-toolbar");
  var container = document.getElementById("cfg-sections");
  if (!toolbar || !container) {
    return;
  }

  var search = document.getElementById("cfg-search");
  var changed = document.getElementById("cfg-changed");
  var phase = document.getElementById("cfg-phase");
  var clear = document.getElementById("cfg-clear");
  var status = document.getElementById("cfg-status");
  var empty = document.getElementById("cfg-empty");
  var jump = document.getElementById("cfg-jump");
  var PREFIX = "cfg-";

  var records = new Map();
  var groups = Array.prototype.map.call(container.querySelectorAll(".cfg-section"), function (details) {
    var rows = Array.prototype.map.call(details.querySelectorAll(".cfg-item"), function (row) {
      var name = row.querySelector(".cfg-name");
      var description = row.querySelector(".cfg-desc");
      var record = {
        row: row,
        text: ((name ? name.textContent : "") + " " + (description ? description.textContent : "")).toLowerCase(),
        phase: row.getAttribute("data-phase"),
        changed: row.getAttribute("data-changed") === "true"
      };
      records.set(row, record);
      return record;
    });
    var count = details.querySelector(".cfg-count");
    var link = document.querySelector('.cfg-index-list a[data-section="' + details.id + '"]');
    var option = jump ? jump.querySelector('option[value="' + details.id + '"]') : null;
    return {
      details: details,
      id: details.id,
      rows: rows,
      count: count,
      baseCount: count ? count.textContent : "",
      item: link ? link.parentElement : null,
      option: option
    };
  });
  var total = groups.reduce(function (sum, group) {
    return sum + group.rows.length;
  }, 0);

  var openIds = defaultOpen();
  var customOpen = false;

  function defaultOpen() {
    var ids = new Set();
    groups.forEach(function (group) {
      if (group.details.hasAttribute("open")) {
        ids.add(group.id);
      }
    });
    return ids;
  }

  function isFiltering() {
    return search.value.trim() !== "" || changed.checked || phase.value !== "";
  }

  function render() {
    var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    var filtering = isFiltering();
    var shown = 0;
    var sectionsShown = 0;
    groups.forEach(function (group) {
      var visible = 0;
      group.rows.forEach(function (record) {
        var match = terms.every(function (term) {
          return record.text.indexOf(term) >= 0;
        }) && (!changed.checked || record.changed) && (!phase.value || record.phase === phase.value);
        record.row.hidden = !match;
        if (match) {
          visible++;
        }
      });
      shown += visible;
      var hide = filtering && visible === 0;
      group.details.hidden = hide;
      if (group.item) {
        group.item.hidden = hide;
      }
      if (group.option) {
        group.option.hidden = hide;
      }
      if (!hide) {
        sectionsShown++;
        group.details.open = filtering ? true : openIds.has(group.id);
      }
      if (group.count) {
        group.count.textContent = filtering
          ? visible + " of " + group.rows.length + " match"
          : group.baseCount;
      }
    });
    empty.hidden = !(filtering && shown === 0);
    clear.hidden = !filtering;
    status.textContent = filtering
      ? shown + " of " + total + " properties in " + sectionsShown + (sectionsShown === 1 ? " section" : " sections")
      : "";
  }

  function writeHash() {
    var params = new URLSearchParams();
    if (search.value.trim() !== "") {
      params.set("q", search.value.trim());
    }
    if (changed.checked) {
      params.set("changed", "1");
    }
    if (phase.value) {
      params.set("phase", phase.value);
    }
    if (customOpen) {
      params.set("open", Array.from(openIds).map(function (id) {
        return id.indexOf(PREFIX) === 0 ? id.slice(PREFIX.length) : id;
      }).join(","));
    }
    var hash = params.toString();
    history.replaceState(null, "", location.pathname + location.search + (hash ? "#" + hash : ""));
  }

  function readHash() {
    var raw = location.hash.replace(/^#/, "");
    if (raw.indexOf("=") < 0) {
      if (raw.indexOf(PREFIX) === 0 && groups.some(function (group) {
        return group.id === raw;
      })) {
        openIds.add(raw);
        return raw;
      }
      return null;
    }
    var params = new URLSearchParams(raw);
    search.value = params.get("q") || "";
    changed.checked = params.get("changed") === "1";
    var wanted = params.get("phase");
    phase.value = wanted === "run" || wanted === "build" ? wanted : "";
    if (params.has("open")) {
      customOpen = true;
      openIds = new Set();
      params.get("open").split(",").filter(Boolean).forEach(function (id) {
        openIds.add(PREFIX + id);
      });
    }
    return null;
  }

  function update() {
    render();
    writeHash();
  }

  function goTo(id) {
    var group = groups.filter(function (candidate) {
      return candidate.id === id;
    })[0];
    if (!group || group.details.hidden) {
      return;
    }
    openIds.add(id);
    customOpen = true;
    render();
    writeHash();
    group.details.scrollIntoView({ block: "start" });
  }

  search.addEventListener("input", update);
  changed.addEventListener("change", update);
  phase.addEventListener("change", update);
  clear.addEventListener("click", function () {
    search.value = "";
    changed.checked = false;
    phase.value = "";
    update();
    search.focus();
  });
  search.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && search.value !== "") {
      search.value = "";
      update();
    }
  });

  groups.forEach(function (group) {
    group.details.addEventListener("toggle", function () {
      if (isFiltering() || group.details.open === openIds.has(group.id)) {
        return;
      }
      if (group.details.open) {
        openIds.add(group.id);
      } else {
        openIds.delete(group.id);
      }
      customOpen = true;
      writeHash();
    });
    if (group.item) {
      group.item.firstElementChild.addEventListener("click", function (event) {
        event.preventDefault();
        goTo(group.id);
      });
    }
  });
  if (jump) {
    jump.addEventListener("change", function () {
      if (jump.value) {
        goTo(jump.value);
        jump.value = "";
      }
    });
  }

  document.addEventListener("keydown", function (event) {
    if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    var target = event.target;
    var tag = target && target.tagName;
    if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (target && target.isContentEditable)) {
      return;
    }
    event.preventDefault();
    search.focus();
    search.select();
  });

  var pendingLabels = {
    "run-saved": "override file, after restart",
    "run-removed": "override removed, after restart",
    "build-saved": "staged, after rebuild",
    "build-removed": "unstaged, after rebuild"
  };

  /* A save answers 204 and changes the file, not the running value, so the row says what will be true
     after the restart or rebuild instead of pretending the effective value moved. */
  container.addEventListener("htmx:afterRequest", function (event) {
    var detail = event.detail;
    var xhr = detail.xhr;
    var row = detail.elt && detail.elt.closest ? detail.elt.closest(".cfg-item") : null;
    if (!row || !xhr || xhr.status < 200 || xhr.status >= 300) {
      return;
    }
    var record = records.get(row);
    var form = detail.elt.closest("form");
    var saved = !!form;
    var label = pendingLabels[row.getAttribute("data-phase") + "-" + (saved ? "saved" : "removed")];
    var source = row.querySelector(".cfg-src");
    if (!label || !source) {
      return;
    }
    var pill = row.querySelector(".cfg-pending");
    if (!pill) {
      pill = document.createElement("span");
      pill.className = "pill cfg-pending";
      source.insertAdjacentElement("afterend", pill);
    }
    pill.textContent = label;
    if (saved && record) {
      record.changed = true;
      row.setAttribute("data-changed", "true");
      row.setAttribute("data-owner", "true");
    }
  });

  var landed = readHash();
  toolbar.hidden = false;
  render();
  if (landed) {
    var target = document.getElementById(landed);
    if (target) {
      target.scrollIntoView({ block: "start" });
    }
  }
}());
