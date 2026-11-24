/* Applies a stored light or dark choice before the stylesheet loads, so a reload never flashes the wrong
   theme. It is a separate synchronous script (no defer) in the head, never inline, because the console serves
   no inline script. The switch in console.js owns the choice itself; "system" stores nothing. */
(function () {
  "use strict";
  try {
    var stored = window.localStorage.getItem("qlawkus-theme");
    if (stored === "light" || stored === "dark") {
      document.documentElement.setAttribute("data-theme", stored);
    }
  } catch (ignored) {
  }
}());
