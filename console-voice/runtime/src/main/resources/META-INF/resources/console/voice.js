(function () {
    "use strict";

    var panel = document.getElementById("voice");
    if (!panel) {
        return;
    }
    var toggle = document.getElementById("voice-toggle");
    var statusEl = document.getElementById("voice-status");
    var adapterEl = document.getElementById("voice-adapter");
    var logEl = document.getElementById("voice-log");
    var errorEl = document.getElementById("voice-error");
    var socketPath = panel.getAttribute("data-socket");
    var hangoverMs = Number(panel.getAttribute("data-hangover-ms")) || 800;
    var threshold = Number(panel.getAttribute("data-speech-threshold")) || 0.02;
    var maxUtteranceMs = Number(panel.getAttribute("data-max-utterance-ms")) || 30000;

    var socket = null;
    var stream = null;
    var audioContext = null;
    var source = null;
    var processor = null;
    var active = false;
    var speaking = false;
    var lastVoiceAt = 0;
    var utteranceStartedAt = 0;
    var agentBusy = false;
    var highestTurn = 0;
    var gate = 0;
    var currentBubble = null;
    var bubbleTurn = -1;
    var playQueue = [];
    var playing = false;
    var currentAudio = null;

    var ERROR_TEXT = {
        "too-many-sessions": "You already have a voice session open.",
        "utterance-too-long": "That utterance was longer than the limit. Speak again.",
        "frame-too-large": "The browser sent an audio frame over the size limit.",
        "transcription-failed": "The speech-to-text adapter failed for that utterance.",
        "speech-failed": "The speech adapter failed for one sentence.",
        "agent-failed": "The agent could not finish that reply.",
        "speech-queue-full": "The speech queue is full, so some sentences are not spoken.",
        "empty-utterance": "Nothing was captured in that utterance."
    };

    function setStatus(text) {
        statusEl.textContent = text;
    }

    function showError(text) {
        errorEl.textContent = text;
        errorEl.hidden = false;
    }

    function clearError() {
        errorEl.textContent = "";
        errorEl.hidden = true;
    }

    function addLine(kind, text) {
        var line = document.createElement("div");
        line.className = "voice-line voice-" + kind;
        line.textContent = text;
        logEl.appendChild(line);
        logEl.scrollTop = logEl.scrollHeight;
        return line;
    }

    function agentBubble(turn) {
        if (currentBubble === null || bubbleTurn !== turn) {
            currentBubble = addLine("agent", "");
            bubbleTurn = turn;
        }
        return currentBubble;
    }

    function send(message) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(typeof message === "string" ? message : JSON.stringify(message));
        }
    }

    function sendFrame(samples) {
        if (!socket || socket.readyState !== WebSocket.OPEN) {
            return;
        }
        var pcm = new DataView(new ArrayBuffer(samples.length * 2));
        for (var i = 0; i < samples.length; i++) {
            var clamped = Math.max(-1, Math.min(1, samples[i]));
            pcm.setInt16(i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true);
        }
        socket.send(pcm.buffer);
    }

    function rmsOf(samples) {
        var sum = 0;
        for (var i = 0; i < samples.length; i++) {
            sum += samples[i] * samples[i];
        }
        return Math.sqrt(sum / samples.length);
    }

    function stopPlayback() {
        playQueue.forEach(function (item) {
            URL.revokeObjectURL(item.url);
        });
        playQueue = [];
        if (currentAudio) {
            currentAudio.pause();
            currentAudio = null;
        }
        playing = false;
    }

    function playNext() {
        var item = playQueue.shift();
        if (!item) {
            playing = false;
            currentAudio = null;
            return;
        }
        playing = true;
        currentAudio = new Audio(item.url);
        var finished = function () {
            URL.revokeObjectURL(item.url);
            if (currentAudio && currentAudio.src === item.url) {
                playNext();
            }
        };
        currentAudio.onended = finished;
        currentAudio.onerror = finished;
        currentAudio.play().catch(finished);
    }

    function enqueueAudio(turn, mimeType, base64) {
        if (turn < gate) {
            return;
        }
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        var url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
        playQueue.push({ turn: turn, url: url });
        if (!playing) {
            playNext();
        }
    }

    function interrupt() {
        send({ type: "interrupt" });
        stopPlayback();
        gate = highestTurn + 1;
        agentBusy = false;
        setStatus("Listening");
    }

    function handleEvent(event) {
        var turn = typeof event.turn === "number" ? event.turn : 0;
        if (turn > highestTurn) {
            highestTurn = turn;
        }
        switch (event.type) {
            case "ready":
                adapterEl.textContent = event.speechBuffered
                    ? "Speech output: buffered, one sentence at a time (" + event.speech + ")."
                    : "Speech output: streamed (" + event.speech + ").";
                adapterEl.setAttribute("data-buffered", event.speechBuffered ? "true" : "false");
                setStatus("Listening");
                break;
            case "transcript":
                if (turn < gate) {
                    break;
                }
                addLine("user", event.text);
                agentBusy = true;
                setStatus("Thinking");
                break;
            case "text":
                if (turn < gate) {
                    break;
                }
                agentBubble(turn).textContent += event.chunk;
                break;
            case "tool":
                if (turn < gate) {
                    break;
                }
                setStatus(event.phase === "start" ? "Using " + event.name : "Thinking");
                break;
            case "audio":
                enqueueAudio(turn, event.mimeType, event.data);
                setStatus("Speaking");
                break;
            case "turn-done":
                if (turn >= gate) {
                    agentBusy = false;
                    setStatus(speaking ? "Hearing you" : "Listening");
                }
                break;
            case "interrupted":
                stopPlayback();
                gate = turn + 1;
                agentBusy = false;
                setStatus("Listening");
                break;
            case "error":
                showError(ERROR_TEXT[event.code] || ("Voice error: " + event.code));
                break;
            case "notice":
                showError(ERROR_TEXT[event.code] || ("Notice: " + event.code));
                break;
            case "closed":
                showError("The voice session closed: " + event.reason + ".");
                stop();
                break;
            default:
                break;
        }
    }

    function onAudio(event) {
        if (!active) {
            return;
        }
        var samples = event.inputBuffer.getChannelData(0);
        var rms = rmsOf(samples);
        var now = performance.now();
        var needed = playing || agentBusy ? threshold * 2 : threshold;
        var voiced = rms >= needed;
        if (voiced) {
            lastVoiceAt = now;
            if (!speaking) {
                speaking = true;
                utteranceStartedAt = now;
                if (playing || agentBusy) {
                    interrupt();
                }
                send({ type: "start", sampleRate: audioContext.sampleRate });
                setStatus("Hearing you");
            }
        }
        if (speaking) {
            sendFrame(samples);
            var quiet = !voiced && now - lastVoiceAt > hangoverMs;
            var tooLong = now - utteranceStartedAt > maxUtteranceMs;
            if (quiet || tooLong) {
                speaking = false;
                send({ type: "utterance-end" });
                setStatus(agentBusy ? "Thinking" : "Listening");
            }
        }
    }

    function openSocket() {
        var scheme = window.location.protocol === "https:" ? "wss://" : "ws://";
        socket = new WebSocket(scheme + window.location.host + socketPath);
        socket.binaryType = "arraybuffer";
        setStatus("Connecting");
        socket.onmessage = function (message) {
            if (typeof message.data !== "string") {
                return;
            }
            var event;
            try {
                event = JSON.parse(message.data);
            } catch (e) {
                return;
            }
            handleEvent(event);
        };
        socket.onclose = function (closed) {
            if (active) {
                showError(closed.code === 1006
                    ? "The voice session could not be opened. Sign in to the console and try again."
                    : "The voice session ended.");
                stop();
            }
        };
    }

    function start() {
        clearError();
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showError("This browser cannot capture the microphone.");
            return;
        }
        navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } })
            .then(function (captured) {
                stream = captured;
                var AudioCtx = window.AudioContext || window.webkitAudioContext;
                audioContext = new AudioCtx();
                source = audioContext.createMediaStreamSource(stream);
                processor = audioContext.createScriptProcessor(4096, 1, 1);
                processor.onaudioprocess = onAudio;
                source.connect(processor);
                processor.connect(audioContext.destination);
                active = true;
                toggle.textContent = "Stop microphone";
                toggle.setAttribute("aria-pressed", "true");
                openSocket();
            })
            .catch(function (err) {
                showError("Microphone unavailable: " + err.name);
                stop();
            });
    }

    function stop() {
        var wasActive = active;
        active = false;
        speaking = false;
        stopPlayback();
        if (wasActive) {
            send({ type: "stop" });
        }
        if (socket) {
            socket.onclose = null;
            socket.close();
            socket = null;
        }
        if (processor) {
            processor.disconnect();
            processor.onaudioprocess = null;
            processor = null;
        }
        if (source) {
            source.disconnect();
            source = null;
        }
        if (stream) {
            stream.getTracks().forEach(function (track) {
                track.stop();
            });
            stream = null;
        }
        if (audioContext) {
            audioContext.close();
            audioContext = null;
        }
        toggle.textContent = "Start microphone";
        toggle.setAttribute("aria-pressed", "false");
        setStatus("Idle");
    }

    toggle.addEventListener("click", function () {
        if (active) {
            stop();
        } else {
            start();
        }
    });
})();
