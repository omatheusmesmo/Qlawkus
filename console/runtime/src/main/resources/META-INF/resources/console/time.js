(function () {
  'use strict';

  var UNITS = [
    ['year', 31536000],
    ['month', 2592000],
    ['week', 604800],
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60]
  ];
  var formatter = typeof Intl !== 'undefined' && typeof Intl.RelativeTimeFormat === 'function'
    ? new Intl.RelativeTimeFormat(document.documentElement.lang || undefined, { numeric: 'auto' })
    : null;

  function relative(then, now) {
    var seconds = Math.round((then - now) / 1000);
    var magnitude = Math.abs(seconds);
    if (magnitude < 45) {
      return formatter.format(0, 'second');
    }
    for (var i = 0; i < UNITS.length; i++) {
      if (magnitude >= UNITS[i][1] || UNITS[i][0] === 'minute') {
        return formatter.format(Math.round(seconds / UNITS[i][1]), UNITS[i][0]);
      }
    }
    return formatter.format(0, 'second');
  }

  function refresh() {
    if (!formatter) {
      return;
    }
    var now = Date.now();
    var times = document.querySelectorAll('time[data-relative][datetime]');
    for (var i = 0; i < times.length; i++) {
      var then = Date.parse(times[i].getAttribute('datetime'));
      if (!isNaN(then)) {
        times[i].textContent = relative(then, now);
      }
    }
  }

  document.addEventListener('DOMContentLoaded', refresh);
  document.addEventListener('htmx:afterSwap', refresh);
  window.setInterval(refresh, 60000);
}());
