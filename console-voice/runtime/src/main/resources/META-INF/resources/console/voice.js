(function () {
    "use strict";

    var panel = document.getElementById("voice");
    if (!panel) {
        return;
    }
    function $(id) {
        return document.getElementById(id);
    }
    var toggle = $("voice-toggle");
    var toggleText = $("voice-toggle-text");
    var interruptButton = $("voice-interrupt");
    var muteButton = $("voice-mute");
    var stateEl = $("voice-state");
    var subEl = $("voice-sub");
    var heardEl = $("voice-heard");
    var replyingEl = $("voice-replying");
    var timerBox = $("voice-timer-box");
    var timerEl = $("voice-timer");
    var timerFill = $("voice-timer-fill");
    var pttButton = $("voice-ptt");
    var calibrateButton = $("voice-calibrate");
    var thresholdEl = $("voice-threshold");
    var languageSelect = $("voice-language");
    var deviceSelect = $("voice-device");
    var logEl = $("voice-log");
    var errorEl = $("voice-error");
    var adapterEl = $("voice-adapter");
    var textForm = $("voice-text-form");
    var textInput = $("voice-text");
    var sendButton = $("voice-send");
    var modeInputs = document.querySelectorAll("input[name='voice-mode']");
    var orbCanvas = $("voice-orb");

    var socketPath = panel.getAttribute("data-socket");
    var workletPath = panel.getAttribute("data-worklet");
    var hangoverMs = Number(panel.getAttribute("data-hangover-ms")) || 800;
    var serverThreshold = Number(panel.getAttribute("data-speech-threshold")) || 0.02;
    var maxUtteranceMs = Number(panel.getAttribute("data-max-utterance-ms")) || 30000;
    var MIN_THRESHOLD = 0.004;
    var CALIBRATE_MS = 1500;
    var INTERRUPT_SETTLE_MS = 400;
    var RECONNECT_DELAYS = [1000, 2000, 4000];
    var TIMER_TICK_MS = 250;
    var REVEAL_TICK_MS = 28;

    var reducedMotion = Boolean(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
    var orb = window.QlawkusVoiceOrb ? window.QlawkusVoiceOrb.create(orbCanvas) : null;
    var displayNames = null;
    try {
        displayNames = new Intl.DisplayNames([document.documentElement.lang || "en"], { type: "language" });
    } catch (e) {
        displayNames = null;
    }

    var socket = null;
    var stream = null;
    var audioContext = null;
    var source = null;
    var worklet = null;
    var sink = null;
    var inputAnalyser = null;
    var outputAnalyser = null;
    var active = false;
    var textOnly = false;
    var muted = false;
    var connected = false;
    var everReady = false;
    var reconnecting = false;
    var reconnectAttempts = 0;
    var reconnectTimer = null;
    var fatal = false;
    var mode = "vad";
    var speaking = false;
    var pttHeld = false;
    var pending = false;
    var agentBusy = false;
    var lastVoiceAt = 0;
    var utteranceStartedAt = 0;
    var highestTurn = 0;
    var gate = 0;
    var interruptedUntil = 0;
    var currentBubble = null;
    var bubbleTurn = -1;
    var revealQueue = "";
    var revealTimer = null;
    var revealTarget = null;
    var playQueue = [];
    var playing = false;
    var currentAudio = null;
    var floor = null;
    var calibration = null;
    var threshold = serverThreshold;
    var detectedLanguage = null;
    var replyLanguage = null;
    var sessionEndsAt = 0;
    var sessionMs = 0;
    var lastState = "";
    var statusToken = 0;
    var offeredLanguages = [];

    var ERROR_TEXT = {
        "too-many-sessions": "You already have a voice session open.",
        "utterance-too-long": "That utterance was longer than the limit. Speak again.",
        "frame-too-large": "The browser sent an audio frame over the size limit.",
        "transcription-failed": "The speech-to-text adapter failed for that utterance.",
        "speech-failed": "The speech adapter failed for one sentence.",
        "agent-failed": "The agent could not finish that reply.",
        "speech-queue-full": "The speech queue is full, so some sentences are not spoken.",
        "empty-utterance": "Nothing was captured in that utterance.",
        "unknown-language": "That language has no speech provider, so it cannot be chosen.",
        "empty-text": "Type a message before sending it.",
        "text-too-long": "That message is longer than the limit. Shorten it and send again."
    };

    var CLOSE_TEXT = {
        "session-time-limit": "The session reached its time limit, so the microphone is off. Start the microphone again to continue the conversation.",
        "server-shutdown": "The server is shutting down, so the voice session has ended.",
        "stopped": "The voice session has ended.",
        "disconnected": "The voice session has ended."
    };

    var MIC_TEXT = {
        "InsecureContext": "The microphone needs a secure page. Open the console over HTTPS or from localhost.",
        "Unsupported": "This browser cannot capture the microphone.",
        "NotAllowedError": "Microphone permission was denied. Allow the microphone for this site in the browser settings, then start again.",
        "SecurityError": "Microphone permission was denied. Allow the microphone for this site in the browser settings, then start again.",
        "NotFoundError": "No microphone was found. Connect one, or choose another device.",
        "DevicesNotFoundError": "No microphone was found. Connect one, or choose another device.",
        "OverconstrainedError": "The selected microphone is not available. Choose another device.",
        "ConstraintNotSatisfiedError": "The selected microphone is not available. Choose another device.",
        "NotReadableError": "The microphone could not be read. Another application may be using it.",
        "TrackStartError": "The microphone could not be read. Another application may be using it.",
        "AudioWorkletUnavailable": "This browser has no AudioWorklet, so the microphone cannot be captured. Use a current Chrome, Edge, Firefox or Safari."
    };

    var STATE_LABEL = {
        idle: "Idle",
        muted: "Muted",
        connecting: "Connecting",
        listening: "Listening",
        hearing: "Hearing you",
        thinking: "Thinking",
        speaking: "Speaking",
        interrupted: "Interrupted"
    };

    function languageName(code) {
        if (!code) {
            return "";
        }
        if (displayNames) {
            try {
                return displayNames.of(code) || code;
            } catch (e) {
                return code;
            }
        }
        return code;
    }

    function setSub(text) {
        subEl.textContent = text;
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

    function flushReveal() {
        if (revealTarget && revealQueue) {
            revealTarget.textContent += revealQueue;
        }
        revealQueue = "";
        if (revealTimer) {
            clearInterval(revealTimer);
            revealTimer = null;
        }
        if (revealTarget) {
            revealTarget.classList.remove("is-typing");
        }
    }

    function agentBubble(turn) {
        if (currentBubble === null || bubbleTurn !== turn) {
            flushReveal();
            currentBubble = addLine("agent", "");
            bubbleTurn = turn;
        }
        return currentBubble;
    }

    function revealText(turn, chunk) {
        var bubble = agentBubble(turn);
        if (reducedMotion) {
            bubble.textContent += chunk;
            return;
        }
        revealTarget = bubble;
        revealQueue += chunk;
        bubble.classList.add("is-typing");
        if (!revealTimer) {
            revealTimer = setInterval(function () {
                var count = Math.max(1, Math.ceil(revealQueue.length / 24));
                revealTarget.textContent += revealQueue.slice(0, count);
                revealQueue = revealQueue.slice(count);
                logEl.scrollTop = logEl.scrollHeight;
                if (!revealQueue) {
                    flushReveal();
                }
            }, REVEAL_TICK_MS);
        }
    }

    function busy() {
        return pending || agentBusy;
    }

    function currentState(now) {
        if (!active && !textOnly) {
            return "idle";
        }
        if (interruptedUntil > now) {
            return "interrupted";
        }
        if (textOnly) {
            return "idle";
        }
        if (!connected) {
            return "connecting";
        }
        if (muted) {
            return "muted";
        }
        if (speaking) {
            return "hearing";
        }
        if (playing) {
            return "speaking";
        }
        if (busy()) {
            return "thinking";
        }
        return "listening";
    }

    function swapStatus(text, state) {
        var token = ++statusToken;
        stateEl.classList.add("is-swapping");
        setTimeout(function () {
            if (token !== statusToken) {
                return;
            }
            stateEl.textContent = text;
            stateEl.setAttribute("data-state", state);
            stateEl.classList.remove("is-swapping");
        }, 180);
    }

    function render() {
        var now = performance.now();
        var state = currentState(now);
        if (state !== lastState) {
            lastState = state;
            var label = textOnly && state === "idle" ? "Text only" : STATE_LABEL[state];
            if (reducedMotion) {
                stateEl.textContent = label;
                stateEl.setAttribute("data-state", state);
            } else {
                swapStatus(label, state);
            }
            setSub(stateHint(state));
            if (orb) {
                orb.setState(state);
            }
        }
        var live = active && !textOnly;
        toggle.setAttribute("data-mode", active ? "stop" : "start");
        var verb = active ? (textOnly ? "End session" : "Stop microphone") : "Start microphone";
        toggle.setAttribute("aria-label", verb);
        toggle.setAttribute("aria-pressed", active ? "true" : "false");
        toggleText.textContent = active ? (textOnly ? "End" : "Stop") : "Start";
        muteButton.disabled = !(live && connected);
        muteButton.setAttribute("aria-pressed", muted ? "true" : "false");
        muteButton.textContent = muted ? "Unmute" : "Mute";
        interruptButton.disabled = !(busy() || playing || speaking);
        pttButton.disabled = !(live && connected && mode === "ptt" && !muted);
        pttButton.classList.toggle("is-held", pttHeld);
        calibrateButton.disabled = !(live && connected);
        languageSelect.disabled = !connected;
        sendButton.disabled = !connected;
        textInput.disabled = !connected;
        panel.classList.toggle("is-text-only", textOnly);
        updateTimer(now);
    }

    function updateTimer(now) {
        if (!sessionEndsAt || !active) {
            timerBox.hidden = true;
            return;
        }
        timerBox.hidden = false;
        var remaining = Math.max(0, sessionEndsAt - now);
        var fraction = sessionMs > 0 ? remaining / sessionMs : 0;
        timerEl.textContent = formatTime(remaining) + " left of " + formatTime(sessionMs);
        timerFill.style.width = (fraction * 100).toFixed(1) + "%";
    }

    function formatTime(ms) {
        var total = Math.ceil(ms / 1000);
        var minutes = Math.floor(total / 60);
        var seconds = total % 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
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
            render();
            return;
        }
        playing = true;
        var element = new Audio(item.url);
        currentAudio = element;
        if (audioContext && outputAnalyser) {
            try {
                audioContext.createMediaElementSource(element).connect(outputAnalyser);
            } catch (error) {
            }
        }
        var finished = function () {
            URL.revokeObjectURL(item.url);
            if (currentAudio === element) {
                playNext();
            }
        };
        element.onended = finished;
        element.onerror = finished;
        element.play().catch(finished);
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

    function interruptReply() {
        if (!busy() && !playing) {
            return;
        }
        send({ type: "interrupt" });
        stopPlayback();
        flushReveal();
        gate = highestTurn + 1;
        pending = false;
        agentBusy = false;
        interruptedUntil = performance.now() + INTERRUPT_SETTLE_MS;
        render();
    }

    function handleEvent(event) {
        var turn = typeof event.turn === "number" ? event.turn : 0;
        if (turn > highestTurn) {
            highestTurn = turn;
        }
        switch (event.type) {
            case "ready":
                onReady(event);
                break;
            case "language":
                languageSelect.value = event.language;
                break;
            case "transcript":
                pending = false;
                if (turn < gate) {
                    break;
                }
                if (!event.typed) {
                    detectedLanguage = event.language || null;
                    heardEl.textContent = detectedLanguage
                        ? "Heard in " + languageName(detectedLanguage)
                        : "Language not detected";
                }
                if (event.text) {
                    addLine("user", event.text);
                    agentBusy = true;
                } else {
                    addLine("notice", "Nothing was recognised in that utterance.");
                    agentBusy = false;
                }
                break;
            case "text":
                if (turn < gate) {
                    break;
                }
                revealText(turn, event.chunk);
                break;
            case "tool":
                if (turn < gate) {
                    break;
                }
                if (event.phase === "start") {
                    addLine("tool", "Using " + event.name + " ...");
                } else {
                    addLine("tool", (event.ok === false ? "Failed: " : "Used ") + event.name);
                }
                break;
            case "audio":
                if (turn < gate) {
                    break;
                }
                replyLanguage = event.language || null;
                replyingEl.textContent = replyLanguage ? "Replying in " + languageName(replyLanguage) : "";
                enqueueAudio(turn, event.mimeType, event.data);
                break;
            case "turn-done":
                if (turn >= gate) {
                    agentBusy = false;
                    pending = false;
                }
                break;
            case "interrupted":
                stopPlayback();
                flushReveal();
                gate = turn + 1;
                agentBusy = false;
                interruptedUntil = performance.now() + INTERRUPT_SETTLE_MS;
                break;
            case "error":
                onError(event, turn);
                break;
            case "notice":
                showError(ERROR_TEXT[event.code] || ("Notice: " + event.code));
                break;
            case "closed":
                endSession(CLOSE_TEXT[event.reason] || ("The voice session closed: " + event.reason + "."));
                break;
            default:
                break;
        }
        render();
    }

    function onReady(event) {
        connected = true;
        everReady = true;
        reconnecting = false;
        reconnectAttempts = 0;
        adapterEl.textContent = event.speechBuffered
            ? "Speech output: buffered, one sentence at a time (" + event.speech + ")."
            : "Speech output: streamed (" + event.speech + ").";
        adapterEl.setAttribute("data-buffered", event.speechBuffered ? "true" : "false");
        offerLanguages(event.languages || [], event.language || "auto");
        sessionMs = Number(event.maxSessionSeconds) * 1000 || 0;
        sessionEndsAt = sessionMs ? performance.now() + sessionMs : 0;
        textInput.maxLength = Number(event.maxTextChars) || 2000;
        setSub(modeHint());
        clearError();
    }

    function stateHint(state) {
        switch (state) {
            case "idle":
                return textOnly ? "The microphone is unavailable. Type your message below." : "Start the microphone to talk with the agent.";
            case "muted":
                return "Muted. The agent will not hear you until you unmute.";
            case "connecting":
                return reconnecting ? "Connection dropped. Reconnecting." : "Connecting to the agent.";
            case "hearing":
                return "The agent answers when you pause.";
            case "thinking":
                return "Working on your reply.";
            case "speaking":
                return "Speak to interrupt, or press Interrupt.";
            case "interrupted":
                return "Reply stopped. Go ahead.";
            default:
                return modeHint();
        }
    }

    function modeHint() {
        if (textOnly) {
            return "The microphone is unavailable. Type your message below.";
        }
        if (muted) {
            return "Muted. The agent will not hear you until you unmute.";
        }
        return mode === "ptt"
            ? "Hold Space or the talk button while you speak."
            : "Speak when you are ready. The agent answers when you pause.";
    }

    function offerLanguages(codes, current) {
        var offered = codes.slice();
        if (current !== "auto" && offered.indexOf(current) < 0) {
            offered.push(current);
        }
        var same = offered.length === offeredLanguages.length && offered.every(function (code, i) {
            return code === offeredLanguages[i];
        });
        if (!same) {
            offeredLanguages = offered;
            languageSelect.innerHTML = "";
            var auto = document.createElement("option");
            auto.value = "auto";
            auto.textContent = "Auto (detect)";
            languageSelect.appendChild(auto);
            offered.forEach(function (code) {
                var option = document.createElement("option");
                option.value = code;
                option.textContent = languageName(code);
                languageSelect.appendChild(option);
            });
        }
        languageSelect.value = current;
    }

    function onError(event, turn) {
        if (event.code === "too-many-sessions") {
            fatal = true;
        }
        if (turn === 0) {
            pending = false;
        }
        showError(ERROR_TEXT[event.code] || ("Voice error: " + event.code));
    }

    function onAudioChunk(samples) {
        if (!active) {
            return;
        }
        if (muted) {
            if (speaking) {
                endUtterance();
            }
            return;
        }
        var rms = rmsOf(samples);
        var now = performance.now();
        if (calibration) {
            calibration.sum += rms;
            calibration.count++;
            if (now >= calibration.until) {
                finishCalibration();
            }
        }
        var echoGuard = playing || busy();
        var needed = echoGuard ? threshold * 2 : threshold;
        var voiced = mode === "ptt" ? pttHeld : rms >= needed;
        if (!speaking && !voiced && !echoGuard && !calibration) {
            learnFloor(rms);
        }
        if (voiced) {
            lastVoiceAt = now;
        }
        if (voiced && !speaking) {
            beginUtterance(now, echoGuard);
        }
        if (speaking) {
            sendFrame(samples);
            if (mode === "ptt") {
                if (!pttHeld) {
                    endUtterance();
                }
            } else {
                var quiet = !voiced && now - lastVoiceAt > hangoverMs;
                var tooLong = now - utteranceStartedAt > maxUtteranceMs;
                if (quiet || tooLong) {
                    endUtterance();
                }
            }
        }
    }

    function beginUtterance(now, echoGuard) {
        speaking = true;
        utteranceStartedAt = now;
        if (echoGuard) {
            interruptReply();
        }
        send({ type: "start", sampleRate: audioContext.sampleRate });
        setSub("Hearing you.");
        render();
    }

    function endUtterance() {
        speaking = false;
        pending = true;
        send({ type: "utterance-end" });
        setSub(modeHint());
        render();
    }

    function learnFloor(rms) {
        if (rms >= serverThreshold) {
            return;
        }
        floor = floor === null ? rms : floor + (rms - floor) * 0.02;
        threshold = adaptiveThreshold();
    }

    function adaptiveThreshold() {
        if (floor === null) {
            return serverThreshold;
        }
        return Math.min(serverThreshold, Math.max(MIN_THRESHOLD, floor * 3 + MIN_THRESHOLD));
    }

    function showThreshold() {
        thresholdEl.textContent = "Threshold " + threshold.toFixed(3)
            + (floor === null ? " (server default)" : " (adapted to this room)")
            + ", maximum " + serverThreshold.toFixed(3) + ".";
    }

    function startCalibration() {
        if (!active || textOnly || !connected || calibration) {
            return;
        }
        calibration = { until: performance.now() + CALIBRATE_MS, sum: 0, count: 0 };
        setSub("Stay quiet for a moment while the room is measured.");
    }

    function finishCalibration() {
        var measured = calibration.count > 0 ? calibration.sum / calibration.count : 0;
        calibration = null;
        floor = measured;
        threshold = adaptiveThreshold();
        showThreshold();
        setSub("Threshold set for this room. " + modeHint());
    }

    function captureMicrophone(device) {
        if (!window.isSecureContext) {
            return Promise.reject({ name: "InsecureContext" });
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            return Promise.reject({ name: "Unsupported" });
        }
        var audio = { channelCount: 1, echoCancellation: true, noiseSuppression: true };
        if (device) {
            audio.deviceId = { exact: device };
        }
        return navigator.mediaDevices.getUserMedia({ audio: audio });
    }

    function setupAudio(captured) {
        var AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx || typeof AudioWorkletNode === "undefined") {
            return Promise.reject({ name: "AudioWorkletUnavailable" });
        }
        audioContext = new AudioCtx();
        if (!audioContext.audioWorklet) {
            return Promise.reject({ name: "AudioWorkletUnavailable" });
        }
        return audioContext.audioWorklet.addModule(workletPath).then(function () {
            source = audioContext.createMediaStreamSource(captured);
            worklet = new AudioWorkletNode(audioContext, "voice-capture", {
                numberOfInputs: 1,
                numberOfOutputs: 1,
                outputChannelCount: [1]
            });
            worklet.port.onmessage = function (message) {
                onAudioChunk(message.data);
            };
            sink = audioContext.createGain();
            sink.gain.value = 0;
            sink.connect(audioContext.destination);
            inputAnalyser = audioContext.createAnalyser();
            inputAnalyser.fftSize = 1024;
            inputAnalyser.smoothingTimeConstant = 0.5;
            outputAnalyser = audioContext.createAnalyser();
            outputAnalyser.fftSize = 1024;
            outputAnalyser.smoothingTimeConstant = 0.5;
            outputAnalyser.connect(audioContext.destination);
            source.connect(inputAnalyser);
            inputAnalyser.connect(sink);
            source.connect(worklet);
            worklet.connect(sink);
            if (orb) {
                orb.setInput(inputAnalyser);
                orb.setOutput(outputAnalyser);
            }
            return audioContext.resume ? audioContext.resume() : null;
        }, function () {
            return Promise.reject({ name: "AudioWorkletUnavailable" });
        });
    }

    function micFailureText(error) {
        var name = error && error.name ? error.name : "unknown";
        return (MIC_TEXT[name] || ("Microphone unavailable (" + name + ").")) + " You can still type below.";
    }

    function start() {
        if (active) {
            return;
        }
        clearError();
        active = true;
        textOnly = false;
        muted = false;
        fatal = false;
        everReady = false;
        reconnectAttempts = 0;
        var device = deviceSelect.value || "";
        setSub("Asking for the microphone.");
        render();
        captureMicrophone(device)
            .then(function (captured) {
                stream = captured;
                refreshDevices();
                return setupAudio(captured);
            })
            .catch(function (error) {
                showError(micFailureText(error));
                textOnly = true;
                releaseAudio();
            })
            .then(function () {
                if (active) {
                    openSocket();
                }
            });
        render();
    }

    function openSocket() {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
        connected = false;
        var scheme = window.location.protocol === "https:" ? "wss://" : "ws://";
        var current = new WebSocket(scheme + window.location.host + socketPath);
        current.binaryType = "arraybuffer";
        socket = current;
        setSub(reconnecting ? "Connection dropped. Reconnecting." : "Connecting to the agent.");
        current.onmessage = function (message) {
            if (socket !== current || typeof message.data !== "string") {
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
        current.onclose = function (closed) {
            if (socket !== current) {
                return;
            }
            socket = null;
            connected = false;
            onSocketLost(closed);
        };
        render();
    }

    function onSocketLost(closed) {
        if (!active) {
            return;
        }
        if (fatal) {
            endSession(null);
            return;
        }
        if (!everReady) {
            endSession(closed.code === 1006
                ? "The voice session could not be opened. Sign in to the console and try again."
                : "The voice session ended.");
            return;
        }
        if (reconnectAttempts >= RECONNECT_DELAYS.length) {
            endSession("The voice connection could not be restored. Start the microphone again to continue.");
            return;
        }
        var delay = RECONNECT_DELAYS[reconnectAttempts];
        reconnectAttempts++;
        reconnecting = true;
        stopPlayback();
        flushReveal();
        speaking = false;
        pending = false;
        agentBusy = false;
        render();
        reconnectTimer = setTimeout(function () {
            reconnectTimer = null;
            if (active) {
                openSocket();
            }
        }, delay);
    }

    function endSession(message) {
        if (message) {
            showError(message);
        }
        stop();
    }

    function releaseAudio() {
        if (worklet) {
            worklet.port.onmessage = null;
            worklet.disconnect();
            worklet = null;
        }
        if (source) {
            source.disconnect();
            source = null;
        }
        if (inputAnalyser) {
            inputAnalyser.disconnect();
            inputAnalyser = null;
        }
        if (outputAnalyser) {
            outputAnalyser.disconnect();
            outputAnalyser = null;
        }
        if (sink) {
            sink.disconnect();
            sink = null;
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
        if (orb) {
            orb.setInput(null);
            orb.setOutput(null);
        }
    }

    function stop() {
        var wasActive = active;
        active = false;
        textOnly = false;
        muted = false;
        connected = false;
        reconnecting = false;
        speaking = false;
        pttHeld = false;
        pending = false;
        agentBusy = false;
        calibration = null;
        interruptedUntil = 0;
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
        stopPlayback();
        flushReveal();
        if (socket) {
            var closing = socket;
            socket = null;
            if (wasActive && closing.readyState === WebSocket.OPEN) {
                closing.send(JSON.stringify({ type: "stop" }));
            }
            closing.close();
        }
        releaseAudio();
        sessionEndsAt = 0;
        setSub("Start the microphone to talk with the agent.");
        render();
    }

    function restartWithDevice() {
        if (!active) {
            return;
        }
        stop();
        setTimeout(start, 0);
    }

    function refreshDevices() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
            return;
        }
        navigator.mediaDevices.enumerateDevices().then(function (devices) {
            var chosen = deviceSelect.value;
            var inputs = devices.filter(function (device) {
                return device.kind === "audioinput";
            });
            deviceSelect.innerHTML = "";
            var fallback = document.createElement("option");
            fallback.value = "";
            fallback.textContent = "Default microphone";
            deviceSelect.appendChild(fallback);
            inputs.forEach(function (device, index) {
                var option = document.createElement("option");
                option.value = device.deviceId;
                option.textContent = device.label || ("Microphone " + (index + 1));
                deviceSelect.appendChild(option);
            });
            deviceSelect.value = inputs.some(function (device) {
                return device.deviceId === chosen;
            }) ? chosen : "";
        }).catch(function () {
            deviceSelect.innerHTML = "";
        });
    }

    function setMode(next) {
        mode = next === "ptt" ? "ptt" : "vad";
        pttHeld = false;
        if (active && !speaking) {
            setSub(modeHint());
        }
        render();
    }

    function toggleMute() {
        if (!active || textOnly || !connected) {
            return;
        }
        muted = !muted;
        pttHeld = false;
        if (muted && speaking) {
            endUtterance();
        }
        setSub(modeHint());
        render();
    }

    function pressToTalk() {
        if (mode !== "ptt" || !active || textOnly || !connected || muted || pttHeld) {
            return;
        }
        pttHeld = true;
        render();
    }

    function releaseToTalk() {
        if (!pttHeld) {
            return;
        }
        pttHeld = false;
        render();
    }

    function isTypingTarget(target) {
        return Boolean(target && target.closest && target.closest("input, select, textarea"));
    }

    toggle.addEventListener("click", function () {
        if (active) {
            stop();
        } else {
            start();
        }
    });

    muteButton.addEventListener("click", toggleMute);

    interruptButton.addEventListener("click", interruptReply);

    languageSelect.addEventListener("change", function () {
        if (connected) {
            send({ type: "language", language: languageSelect.value });
        }
    });

    deviceSelect.addEventListener("change", restartWithDevice);

    calibrateButton.addEventListener("click", startCalibration);

    Array.prototype.forEach.call(modeInputs, function (input) {
        input.addEventListener("change", function () {
            setMode(input.value);
        });
    });

    pttButton.addEventListener("pointerdown", function (event) {
        event.preventDefault();
        pressToTalk();
    });
    pttButton.addEventListener("pointerup", releaseToTalk);
    pttButton.addEventListener("pointerleave", releaseToTalk);
    pttButton.addEventListener("pointercancel", releaseToTalk);

    window.addEventListener("keydown", function (event) {
        if (event.code === "Escape" && (busy() || playing)) {
            interruptReply();
            return;
        }
        if (event.code !== "Space" || mode !== "ptt" || isTypingTarget(event.target)) {
            return;
        }
        event.preventDefault();
        if (!event.repeat) {
            pressToTalk();
        }
    });

    window.addEventListener("keyup", function (event) {
        if (event.code === "Space" && mode === "ptt" && !isTypingTarget(event.target)) {
            event.preventDefault();
            releaseToTalk();
        }
    });

    textForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var text = textInput.value.trim();
        if (!text || !connected) {
            return;
        }
        if (speaking) {
            speaking = false;
            pttHeld = false;
        }
        stopPlayback();
        flushReveal();
        gate = highestTurn + 1;
        pending = true;
        send({ type: "text", text: text });
        textInput.value = "";
        render();
    });

    if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
        navigator.mediaDevices.addEventListener("devicechange", refreshDevices);
    }

    setInterval(render, TIMER_TICK_MS);

    showThreshold();
    refreshDevices();
    render();
})();
