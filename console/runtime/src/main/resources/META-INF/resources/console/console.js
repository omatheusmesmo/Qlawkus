/* Feedback for the console's actions, in three shared parts. A toast region in the layout announces the
   outcome of anything the owner did (window.qlawkusToast, also used by the scripts that do not go through
   HTMX). The control that was used is disabled and marked busy while its request runs, so a second click
   cannot send it twice. A rejected form points at the field that caused it: aria-invalid, aria-describedby
   on the message that explains it, and focus on the first such field. Every message is set as text. */
(function () {
  "use strict";

  var region = document.getElementById("qk-toasts");
  var nextId = 0;

  function dismiss(toast) {
    if (toast.parentNode) {
      toast.parentNode.removeChild(toast);
    }
  }

  window.qlawkusToast = function (text, kind) {
    if (!region || !text) {
      return;
    }
    var error = kind === "error";
    var toast = document.createElement("div");
    toast.className = "toast " + (error ? "toast-err" : "toast-ok");
    toast.setAttribute("role", error ? "alert" : "status");
    var message = document.createElement("span");
    message.className = "toast-text";
    message.textContent = text;
    var close = document.createElement("button");
    close.type = "button";
    close.className = "toast-close";
    close.setAttribute("aria-label", "Dismiss notification");
    close.textContent = "\u00d7";
    close.addEventListener("click", function () {
      dismiss(toast);
    });
    toast.append(message, close);
    region.appendChild(toast);
    while (region.children.length > 4) {
      dismiss(region.firstElementChild);
    }
    window.setTimeout(function () {
      dismiss(toast);
    }, error ? 12000 : 5000);
  };

  function submitters(element) {
    var form = element.tagName === "FORM" ? element : element.closest("form");
    if (!form) {
      return [element];
    }
    return Array.prototype.slice.call(form.querySelectorAll("button[type=submit], button:not([type])"));
  }

  function busy(element, on) {
    submitters(element).forEach(function (button) {
      button.disabled = on;
      button.classList.toggle("is-busy", on);
      if (on) {
        button.setAttribute("aria-busy", "true");
      } else {
        button.removeAttribute("aria-busy");
      }
    });
  }

  function clearInvalid(field) {
    field.removeAttribute("aria-invalid");
    field.removeAttribute("aria-describedby");
  }

  function markInvalid(element, message) {
    var form = element.tagName === "FORM" ? element : element.closest("form");
    if (!form || !message.id) {
      return;
    }
    var named = null;
    try {
      named = JSON.parse(message.getAttribute("data-body") || "null");
    } catch (ignored) {
    }
    var field = named && named.field && form.elements.namedItem(named.field);
    if (!field) {
      var controls = form.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), select, textarea");
      field = controls.length === 1 ? controls[0] : null;
    }
    if (field && field.setAttribute) {
      field.setAttribute("aria-invalid", "true");
      field.setAttribute("aria-describedby", message.id);
      field.addEventListener("input", function () {
        clearInvalid(field);
      }, { once: true });
      field.focus();
    }
  }

  document.addEventListener("htmx:beforeRequest", function (event) {
    if (event.detail.elt) {
      busy(event.detail.elt, true);
    }
  });

  document.addEventListener("htmx:afterRequest", function (event) {
    var detail = event.detail;
    var source = detail.elt;
    var xhr = detail.xhr;
    if (source) {
      busy(source, false);
    }
    var target = detail.target;
    if (!target || !xhr || !(xhr.status === 204 || xhr.status >= 400)) {
      return;
    }
    var ok = xhr.status === 204;
    if (ok && source && source.hasAttribute("data-refresh-on-success")) {
      window.location.reload();
      return;
    }
    var text;
    var body = null;
    if (ok) {
      var announcer = source && (source.hasAttribute("data-ok") ? source : source.querySelector("[data-ok]"));
      text = (announcer && announcer.getAttribute("data-ok")) || "Done.";
    } else {
      text = xhr.status === 404 ? "Nothing to remove: no override is set." : "Request failed (" + xhr.status + ").";
      try {
        body = JSON.parse(xhr.responseText);
        if (body && body.error) {
          text = body.error;
        }
      } catch (ignored) {
      }
    }
    var message = document.createElement("div");
    message.className = "result-msg " + (ok ? "result-ok" : "result-err");
    message.setAttribute("role", ok ? "status" : "alert");
    message.id = "qk-msg-" + (++nextId);
    if (body && body.field) {
      message.setAttribute("data-body", JSON.stringify({ field: String(body.field) }));
    }
    message.textContent = text;
    target.replaceChildren(message);
    window.qlawkusToast(text, ok ? "ok" : "error");
    if (!ok && source) {
      markInvalid(source, message);
    }
  });
}());

/* The navigation is a drawer below 900px. Focus moves into it on open, Tab stays inside while it is open,
   Escape and a click on the backdrop close it, and focus returns to the button that opened it. */
(function () {
  var toggle = document.getElementById("nav-toggle");
  var nav = document.getElementById("site-nav");
  var backdrop = document.getElementById("nav-backdrop");
  var sidebar = nav && nav.closest(".sidebar");
  if (!toggle || !nav || !backdrop || !sidebar) {
    return;
  }
  var narrow = window.matchMedia("(max-width: 900px)");

  function isOpen() {
    return sidebar.hasAttribute("data-open");
  }

  function setOpen(open) {
    if (open === isOpen()) {
      return;
    }
    sidebar.toggleAttribute("data-open", open);
    backdrop.hidden = !open;
    toggle.setAttribute("aria-expanded", String(open));
    if (open) {
      var first = nav.querySelector("a");
      if (first) {
        first.focus();
      }
    } else {
      toggle.focus();
    }
  }

  toggle.addEventListener("click", function () {
    setOpen(!isOpen());
  });
  backdrop.addEventListener("click", function () {
    setOpen(false);
  });
  document.addEventListener("keydown", function (event) {
    if (!isOpen()) {
      return;
    }
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (event.key !== "Tab") {
      return;
    }
    var links = nav.querySelectorAll("a");
    var first = links[0];
    var last = links[links.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      toggle.focus();
    } else if (event.shiftKey && document.activeElement === toggle) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      toggle.focus();
    } else if (!event.shiftKey && document.activeElement === toggle) {
      event.preventDefault();
      first.focus();
    }
  });
  narrow.addEventListener("change", function (event) {
    if (!event.matches && isOpen()) {
      sidebar.removeAttribute("data-open");
      backdrop.hidden = true;
      toggle.setAttribute("aria-expanded", "false");
    }
  });
})();
