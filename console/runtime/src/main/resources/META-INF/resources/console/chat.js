(function () {
  'use strict';

  var form = document.getElementById('chat-form');
  if (!form) {
    return;
  }

  var log = document.getElementById('chat-log');
  var empty = document.getElementById('chat-empty');
  var errorLine = document.getElementById('chat-error');
  var input = document.getElementById('chat-input');
  var send = document.getElementById('chat-send');
  var endpoint = form.getAttribute('data-endpoint');
  var mode = form.getAttribute('data-tool-progress') || 'preview';
  var streaming = false;
  var stick = true;

  function make(tag, className, text) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined) {
      node.textContent = text;
    }
    return node;
  }

  function clear(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function nearBottom() {
    return log.scrollHeight - log.scrollTop - log.clientHeight < 60;
  }

  function follow() {
    if (stick) {
      log.scrollTop = log.scrollHeight;
    }
  }

  log.addEventListener('scroll', function () {
    stick = nearBottom();
  });

  function relative(ts) {
    var seconds = Math.round((Date.now() - ts) / 1000);
    if (seconds < 10) {
      return 'just now';
    }
    if (seconds < 60) {
      return seconds + 's ago';
    }
    var minutes = Math.round(seconds / 60);
    if (minutes < 60) {
      return minutes + ' min ago';
    }
    var hours = Math.round(minutes / 60);
    if (hours < 24) {
      return hours + ' h ago';
    }
    return new Date(ts).toLocaleString();
  }

  function stamp() {
    var ts = Date.now();
    var node = make('time', 'msg-time', relative(ts));
    node.setAttribute('data-ts', String(ts));
    node.setAttribute('datetime', new Date(ts).toISOString());
    node.title = new Date(ts).toLocaleString();
    return node;
  }

  setInterval(function () {
    var nodes = log.querySelectorAll('time[data-ts]');
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].textContent = relative(Number(nodes[i].getAttribute('data-ts')));
    }
  }, 15000);

  function duration(ms) {
    if (ms < 1000) {
      return Math.round(ms) + ' ms';
    }
    return (ms / 1000).toFixed(1) + ' s';
  }

  function inline(parent, text) {
    var parts = text.split(/(`[^`\n]+`|\*\*[^*\n]+\*\*)/);
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (!part) {
        continue;
      }
      if (i % 2 === 1) {
        if (part.charAt(0) === '`') {
          parent.appendChild(make('code', '', part.slice(1, -1)));
        } else {
          parent.appendChild(make('strong', '', part.slice(2, -2)));
        }
      } else {
        parent.appendChild(document.createTextNode(part));
      }
    }
  }

  var FENCE = /^\s*```([\w+#.-]*)\s*$/;
  var CLOSE = /^\s*```\s*$/;
  var ITEM = /^\s*([-*+]|\d+[.)])\s+(.*)$/;
  var HEADING = /^#{1,6}\s+(.*)$/;

  function markdown(container, text) {
    clear(container);
    var lines = text.split('\n');
    var paragraph = [];
    var i = 0;

    function flush() {
      if (paragraph.length) {
        var p = make('p');
        inline(p, paragraph.join('\n'));
        container.appendChild(p);
        paragraph = [];
      }
    }

    while (i < lines.length) {
      var line = lines[i];
      if (FENCE.test(line)) {
        flush();
        i++;
        var code = [];
        while (i < lines.length && !CLOSE.test(lines[i])) {
          code.push(lines[i]);
          i++;
        }
        i++;
        var pre = make('pre');
        pre.appendChild(make('code', '', code.join('\n')));
        container.appendChild(pre);
        continue;
      }
      var item = ITEM.exec(line);
      if (item) {
        flush();
        var list = make(/\d/.test(item[1]) ? 'ol' : 'ul');
        while (i < lines.length) {
          var next = ITEM.exec(lines[i]);
          if (!next) {
            break;
          }
          var li = make('li');
          inline(li, next[2]);
          list.appendChild(li);
          i++;
        }
        container.appendChild(list);
        continue;
      }
      var heading = HEADING.exec(line);
      if (heading) {
        flush();
        var h = make('p', 'md-heading');
        inline(h, heading[1]);
        container.appendChild(h);
        i++;
        continue;
      }
      if (line.trim() === '') {
        flush();
      } else {
        paragraph.push(line);
      }
      i++;
    }
    flush();
  }

  function addMessage(role) {
    if (empty && empty.parentNode) {
      empty.parentNode.removeChild(empty);
      empty = null;
    }
    var message = make('div', 'msg msg-' + role);
    var bubble = make('div', 'bubble bubble-' + role);
    var meta = make('div', 'msg-meta');
    meta.appendChild(stamp());
    message.appendChild(bubble);
    message.appendChild(meta);
    log.appendChild(message);
    return { root: message, bubble: bubble, meta: meta };
  }

  function copyText(text, button) {
    function done() {
      button.textContent = 'Copied';
      setTimeout(function () {
        button.textContent = 'Copy';
      }, 1500);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () {});
      return;
    }
    var area = make('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand('copy');
      done();
    } catch (e) {
      button.textContent = 'Copy failed';
    }
    document.body.removeChild(area);
  }

  function Reply() {
    var message = addMessage('assistant');
    this.root = message.root;
    this.bubble = message.bubble;
    this.meta = message.meta;
    this.current = null;
    this.texts = [];
    this.tools = {};
    this.thinking = make('div', 'thinking');
    this.thinking.appendChild(make('span', 'thinking-label', 'Thinking'));
    for (var i = 0; i < 3; i++) {
      this.thinking.appendChild(make('span', 'thinking-dot'));
    }
    this.bubble.appendChild(this.thinking);
    this.copy = null;
  }

  Reply.prototype.settle = function () {
    if (this.thinking && this.thinking.parentNode) {
      this.thinking.parentNode.removeChild(this.thinking);
    }
    this.thinking = null;
  };

  Reply.prototype.plain = function () {
    var out = [];
    for (var i = 0; i < this.texts.length; i++) {
      out.push(this.texts[i].text);
    }
    return out.join('\n\n');
  };

  Reply.prototype.hasText = function () {
    return this.texts.length > 0;
  };

  Reply.prototype.text = function (chunk) {
    this.settle();
    if (!this.current) {
      this.current = { node: make('div', 'md'), text: '' };
      this.texts.push(this.current);
      this.bubble.appendChild(this.current.node);
    }
    this.current.text += chunk;
    markdown(this.current.node, this.current.text);
    this.offerCopy();
    follow();
  };

  Reply.prototype.offerCopy = function () {
    if (this.copy) {
      return;
    }
    var self = this;
    this.copy = make('button', 'msg-copy', 'Copy');
    this.copy.type = 'button';
    this.copy.addEventListener('click', function () {
      copyText(self.plain(), self.copy);
    });
    this.meta.appendChild(this.copy);
  };

  Reply.prototype.toolStart = function (data) {
    if (mode === 'off') {
      return;
    }
    this.settle();
    this.current = null;
    var card = make('div', 'tool is-running');
    var mark = make('span', 'tool-mark');
    mark.setAttribute('aria-hidden', 'true');
    card.appendChild(mark);
    card.appendChild(make('span', 'tool-name', data.name || 'tool'));
    var time = make('span', 'tool-time');
    var args = null;
    if (data.arguments && mode === 'preview') {
      card.appendChild(make('span', 'tool-detail', data.arguments));
    }
    card.appendChild(time);
    if (data.arguments && mode === 'verbose') {
      args = make('details', 'tool-args');
      args.appendChild(make('summary', '', 'arguments'));
      args.appendChild(make('pre', '', data.arguments));
      card.appendChild(args);
    }
    this.bubble.appendChild(card);
    this.tools[data.id] = { card: card, mark: mark, time: time };
    follow();
  };

  Reply.prototype.toolEnd = function (data) {
    var tool = this.tools[data.id];
    if (!tool) {
      return;
    }
    tool.card.classList.remove('is-running');
    tool.card.classList.add(data.ok ? 'is-ok' : 'is-failed');
    tool.mark.textContent = data.ok ? '✓' : '✗';
    tool.time.textContent = duration(data.durationMs || 0);
    tool.card.setAttribute('data-ms', String(data.durationMs || 0));
    follow();
  };

  Reply.prototype.collapse = function () {
    var children = Array.prototype.slice.call(this.bubble.children);
    var run = [];

    function group() {
      if (!run.length) {
        return;
      }
      var total = 0;
      for (var i = 0; i < run.length; i++) {
        total += Number(run[i].getAttribute('data-ms')) || 0;
      }
      var details = make('details', 'tool-group');
      details.appendChild(make('summary', '', run.length + (run.length === 1 ? ' tool, ' : ' tools, ') +
        duration(total)));
      run[0].parentNode.insertBefore(details, run[0]);
      for (var j = 0; j < run.length; j++) {
        details.appendChild(run[j]);
      }
      run = [];
    }

    for (var k = 0; k < children.length; k++) {
      if (children[k].classList.contains('tool')) {
        run.push(children[k]);
      } else {
        group();
      }
    }
    group();
  };

  Reply.prototype.finish = function (summary) {
    this.settle();
    for (var id in this.tools) {
      if (Object.prototype.hasOwnProperty.call(this.tools, id)) {
        this.tools[id].card.classList.remove('is-running');
      }
    }
    this.collapse();
    if (summary && summary.toolCalls > 0) {
      var label = summary.toolCalls + (summary.toolCalls === 1 ? ' tool, ' : ' tools, ') +
        duration(summary.durationMs || 0);
      var node = make('span', 'msg-summary', label);
      if (summary.inputTokens != null && summary.outputTokens != null) {
        node.title = summary.inputTokens + ' tokens in, ' + summary.outputTokens + ' tokens out';
      }
      this.meta.insertBefore(node, this.meta.firstChild.nextSibling);
    }
    follow();
  };

  function showError(message) {
    errorLine.textContent = message;
    errorLine.hidden = false;
  }

  function clearError() {
    errorLine.textContent = '';
    errorLine.hidden = true;
  }

  function setStreaming(on) {
    streaming = on;
    send.disabled = on;
    input.disabled = on;
    form.classList.toggle('is-streaming', on);
    if (!on) {
      input.focus({ preventScroll: true });
    }
  }

  function fitInput() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 160) + 'px';
  }

  function statusMessage(status) {
    if (status === 401 || status === 403) {
      return 'Not authorized (' + status + '). Reload the page and sign in again.';
    }
    if (status === 400) {
      return 'The agent rejected that message (400).';
    }
    return 'The agent could not answer (HTTP ' + status + ').';
  }

  function consume(reader, onEvent) {
    var decoder = new TextDecoder('utf-8');
    var pending = '';
    var name = '';
    var data = [];

    function line(text) {
      if (text === '') {
        if (data.length) {
          onEvent(name || 'message', data.join('\n'));
        }
        name = '';
        data = [];
      } else if (text.indexOf('event:') === 0) {
        name = text.substring(6).trim();
      } else if (text.indexOf('data:') === 0) {
        data.push(text.substring(5));
      }
    }

    function pump() {
      return reader.read().then(function (result) {
        if (result.done) {
          pending += decoder.decode();
          if (pending) {
            line(pending);
          }
          line('');
          return;
        }
        pending += decoder.decode(result.value, { stream: true });
        var lines = pending.split(/\r\n|\r|\n/);
        pending = lines.pop();
        for (var i = 0; i < lines.length; i++) {
          line(lines[i]);
        }
        return pump();
      });
    }

    return pump();
  }

  function parse(payload) {
    try {
      return JSON.parse(payload);
    } catch (e) {
      return null;
    }
  }

  function ask(message) {
    clearError();
    stick = true;
    var mine = addMessage('user');
    mine.bubble.textContent = message;
    var reply = new Reply();
    follow();
    setStreaming(true);

    var failed = false;
    var summary = null;

    fetch(endpoint, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      body: JSON.stringify({ message: message, toolProgress: mode })
    }).then(function (response) {
      if (!response.ok) {
        failed = true;
        showError(statusMessage(response.status));
        return;
      }
      return consume(response.body.getReader(), function (event, payload) {
        if (event === 'message') {
          reply.text(payload);
          return;
        }
        var body = parse(payload);
        if (!body) {
          return;
        }
        if (event === 'tool-start') {
          reply.toolStart(body);
        } else if (event === 'tool-end') {
          reply.toolEnd(body);
        } else if (event === 'done') {
          summary = body;
        }
      });
    }).catch(function () {
      failed = true;
      showError('Lost the connection to the agent. Check your network and try again.');
    }).then(function () {
      reply.finish(summary);
      var worked = Object.keys(reply.tools).length > 0 || (summary && summary.toolCalls > 0);
      if (!reply.hasText() && !worked) {
        if (!failed) {
          showError('The agent sent no reply.');
        }
        reply.root.parentNode.removeChild(reply.root);
      } else if (failed) {
        reply.bubble.classList.add('is-cut');
      }
      setStreaming(false);
      follow();
    });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var message = input.value.trim();
    if (streaming || !message) {
      return;
    }
    input.value = '';
    fitInput();
    ask(message);
  });

  input.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  input.addEventListener('input', fitInput);
  input.focus({ preventScroll: true });
})();
