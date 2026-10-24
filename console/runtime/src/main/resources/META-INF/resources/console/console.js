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
  var text;
  if (ok) {
    var source = detail.elt;
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
