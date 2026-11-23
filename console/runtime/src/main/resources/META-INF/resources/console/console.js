/* Feedback for the console's HTMX actions. The admin endpoints answer 204 with no body on success and
   a JSON error on failure, and htmx swaps neither, so without this a save or reset shows nothing. */
document.addEventListener("htmx:afterRequest", function (event) {
  var detail = event.detail;
  var target = detail.target;
  var xhr = detail.xhr;
  if (!target || !xhr || !(xhr.status === 204 || xhr.status >= 400)) {
    return;
  }
  var ok = xhr.status === 204;
  var source = detail.elt;
  if (ok && source && source.hasAttribute("data-refresh-on-success")) {
    window.location.reload();
    return;
  }
  var text;
  if (ok) {
    var announcer = source && (source.hasAttribute("data-ok") ? source : source.querySelector("[data-ok]"));
    text = (announcer && announcer.getAttribute("data-ok")) || "Done.";
  } else {
    text = xhr.status === 404 ? "Nothing to remove: no override is set." : "Request failed (" + xhr.status + ").";
    try {
      var body = JSON.parse(xhr.responseText);
      if (body && body.error) {
        text = body.error;
      }
    } catch (ignored) {
    }
  }
  var message = document.createElement("div");
  message.className = "result-msg " + (ok ? "result-ok" : "result-err");
  message.setAttribute("role", ok ? "status" : "alert");
  message.textContent = text;
  target.replaceChildren(message);
});

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
