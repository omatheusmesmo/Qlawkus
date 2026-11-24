/* The one confirmation dialog of the console. A control declares what it will do instead of carrying a
   native confirm string: data-confirm-action (the verb, also the button label), data-confirm-target (what
   it acts on) and data-confirm (what follows). htmx requests are held back until the owner agrees, and
   plain scripts ask through window.qlawkusConfirm, which resolves true only on an explicit confirmation.
   Cancel is focused first, Escape and a click on the backdrop cancel, and every value is set as text. */
(function () {
  "use strict";

  var dialog = document.getElementById("qk-confirm");
  if (!dialog) {
    return;
  }
  var title = document.getElementById("qk-confirm-title");
  var target = document.getElementById("qk-confirm-target");
  var body = document.getElementById("qk-confirm-body");
  var ok = document.getElementById("qk-confirm-ok");
  var pending = null;

  function ask(options) {
    return new Promise(function (resolve) {
      if (pending || typeof dialog.showModal !== "function") {
        resolve(false);
        return;
      }
      var action = options.action || "Confirm";
      pending = resolve;
      title.textContent = action;
      ok.textContent = action;
      target.textContent = options.target || "";
      target.hidden = !options.target;
      body.textContent = options.message || "";
      body.hidden = !options.message;
      dialog.returnValue = "";
      dialog.showModal();
    });
  }

  dialog.addEventListener("close", function () {
    var resolve = pending;
    pending = null;
    if (resolve) {
      resolve(dialog.returnValue === "confirm");
    }
  });

  dialog.addEventListener("click", function (event) {
    if (event.target === dialog) {
      dialog.close("");
    }
  });

  document.addEventListener("htmx:confirm", function (event) {
    var element = event.detail.elt;
    if (!element || !element.hasAttribute("data-confirm")) {
      return;
    }
    event.preventDefault();
    ask({
      action: element.getAttribute("data-confirm-action"),
      target: element.getAttribute("data-confirm-target"),
      message: element.getAttribute("data-confirm")
    }).then(function (confirmed) {
      if (confirmed) {
        event.detail.issueRequest(true);
      }
    });
  });

  window.qlawkusConfirm = ask;
}());
