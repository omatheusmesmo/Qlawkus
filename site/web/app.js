(function () {
  var RESET_MS = 2000;

  function copy(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var area = document.createElement('textarea');
      area.value = text;
      area.setAttribute('readonly', '');
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      var ok = document.execCommand('copy');
      document.body.removeChild(area);
      return ok ? resolve() : reject(new Error('copy refused'));
    });
  }

  function attach(block) {
    var pre = block.querySelector('pre');
    if (!pre || block.querySelector('.copy-button')) { return; }
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy-button';
    button.textContent = 'Copy';
    button.setAttribute('aria-label', 'Copy code to the clipboard');
    var timer;
    button.addEventListener('click', function () {
      copy(pre.innerText.replace(/\n$/, '')).then(function () {
        button.textContent = 'Copied';
        button.setAttribute('data-state', 'copied');
      }, function () {
        button.textContent = 'Press Ctrl+C';
        button.setAttribute('data-state', 'failed');
      }).then(function () {
        clearTimeout(timer);
        timer = setTimeout(function () {
          button.textContent = 'Copy';
          button.removeAttribute('data-state');
        }, RESET_MS);
      });
    });
    block.appendChild(button);
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.doc-body .listingblock, .doc-body .literalblock').forEach(attach);
  });
})();
