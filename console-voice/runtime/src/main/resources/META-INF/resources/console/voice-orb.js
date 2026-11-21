(function () {
    "use strict";

    var TOKENS = ["--faint", "--muted", "--accent", "--accent-strong", "--speak", "--danger"];
    var FALLBACK_RGB = [140, 134, 146];
    var TICKS = 96;
    var RINGS = 3;
    var HALO = 5;
    var WAVES = 4;
    var RIPPLES = 6;
    var RIPPLE_LIFE = 1.2;
    var RIPPLE_GAP = 0.22;
    var TWO_PI = Math.PI * 2;
    var FRAME_MS = 1000 / 62;
    var PARAM_RATE = 4.5;
    var COLOUR_RATE = 3.5;

    var STATES = {
        idle: { color: "--faint", glow: 0.16, core: 0.96, ticks: 0, comet: 0, waves: 0, breath: 0.035, spin: 0.16, source: "", ripple: 0 },
        muted: { color: "--faint", glow: 0.12, core: 0.94, ticks: 0, comet: 0, waves: 0, breath: 0.01, spin: 0.06, source: "", ripple: 0 },
        connecting: { color: "--muted", glow: 0.14, core: 0.9, ticks: 0.12, comet: 0, waves: 0, breath: 0.06, spin: 0.5, source: "", ripple: 0 },
        listening: { color: "--accent", glow: 0.3, core: 1.0, ticks: 0.9, comet: 0, waves: 0, breath: 0.02, spin: 0.1, source: "input", ripple: 0 },
        hearing: { color: "--accent-strong", glow: 0.5, core: 1.04, ticks: 1, comet: 0, waves: 0, breath: 0, spin: 0.22, source: "input", ripple: 1 },
        thinking: { color: "--muted", glow: 0.12, core: 0.9, ticks: 0, comet: 1, waves: 0, breath: 0.02, spin: 0.55, source: "", ripple: 0 },
        speaking: { color: "--speak", glow: 0.4, core: 1.0, ticks: 0, comet: 0, waves: 1, breath: 0, spin: 0.12, source: "output", ripple: 0 },
        interrupted: { color: "--danger", glow: 0.1, core: 0.7, ticks: 0, comet: 0, waves: 0, breath: 0, spin: 0.05, source: "", ripple: 0 }
    };

    function approach(current, target, rate, dt) {
        return current + (target - current) * (1 - Math.exp(-rate * dt));
    }

    function clamp(value, low, high) {
        return Math.max(low, Math.min(high, value));
    }

    function readPalette(palette) {
        var probe = document.createElement("span");
        probe.style.display = "none";
        document.body.appendChild(probe);
        var computed = window.getComputedStyle(probe);
        TOKENS.forEach(function (token) {
            probe.style.color = "var(" + token + ")";
            var parts = (computed.color || "").match(/[\d.]+/g);
            palette[token] = parts && parts.length >= 3
                ? [Number(parts[0]), Number(parts[1]), Number(parts[2])]
                : FALLBACK_RGB;
        });
        document.body.removeChild(probe);
        return palette;
    }

    function rgbString(rgb) {
        return "rgb(" + Math.round(rgb[0]) + "," + Math.round(rgb[1]) + "," + Math.round(rgb[2]) + ")";
    }

    function lighten(rgb, amount) {
        return [
            rgb[0] + (255 - rgb[0]) * amount,
            rgb[1] + (255 - rgb[1]) * amount,
            rgb[2] + (255 - rgb[2]) * amount
        ];
    }

    function rmsOf(buffer) {
        var sum = 0;
        for (var i = 0; i < buffer.length; i++) {
            sum += buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / buffer.length);
    }

    function create(canvas) {
        var ctx = canvas.getContext("2d");
        var palette = readPalette({});
        var reducedQuery = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;
        var darkQuery = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;
        var reduced = Boolean(reducedQuery && reducedQuery.matches);
        var target = STATES.idle;
        var cur = {
            rgb: palette["--faint"].slice(), glow: target.glow, core: target.core, ticks: target.ticks,
            comet: target.comet, waves: target.waves, breath: target.breath, spin: target.spin, ripple: 0
        };
        var level = 0;
        var previousLevel = 0;
        var phase = 0;
        var clock = 0;
        var lastRipple = -10;
        var rippleBirth = new Float64Array(RIPPLES);
        var rippleSlot = 0;
        var tickValues = new Float64Array(TICKS);
        var spectrum = new Float64Array(TICKS);
        var input = null;
        var output = null;
        var timeBuffers = { input: null, output: null };
        var freqBuffers = { input: null, output: null };
        var width = 0;
        var height = 0;
        var dpr = 1;
        var running = false;
        var lastFrame = 0;
        var lastStatic = 0;
        var colourText = "";
        var litText = "";

        function measure() {
            var rect = canvas.getBoundingClientRect();
            width = rect.width;
            height = rect.height;
            dpr = Math.min(2, window.devicePixelRatio || 1);
            canvas.width = Math.max(1, Math.round(width * dpr));
            canvas.height = Math.max(1, Math.round(height * dpr));
        }

        function analyserFor(source) {
            return source === "input" ? input : source === "output" ? output : null;
        }

        function readLevel(source) {
            var analyser = analyserFor(source);
            if (!analyser) {
                return 0;
            }
            var buffer = timeBuffers[source];
            analyser.getFloatTimeDomainData(buffer);
            return clamp(rmsOf(buffer) * 5, 0, 1);
        }

        function readSpectrum(source) {
            var analyser = analyserFor(source);
            if (!analyser) {
                return false;
            }
            var bins = freqBuffers[source];
            analyser.getByteFrequencyData(bins);
            var low = 2;
            var high = Math.max(low + 1, Math.floor(bins.length * 0.3));
            for (var i = 0; i < TICKS; i++) {
                var position = low * Math.pow(high / low, i / (TICKS - 1));
                spectrum[i] = clamp(bins[Math.floor(position)] / 255 * 1.5, 0, 1);
            }
            return true;
        }

        function step(dt) {
            clock += dt;
            cur.glow = approach(cur.glow, target.glow, PARAM_RATE, dt);
            cur.core = approach(cur.core, target.core, PARAM_RATE * 1.4, dt);
            cur.ticks = approach(cur.ticks, reduced ? 0 : target.ticks, PARAM_RATE, dt);
            cur.comet = approach(cur.comet, reduced ? 0 : target.comet, PARAM_RATE, dt);
            cur.waves = approach(cur.waves, reduced ? 0 : target.waves, PARAM_RATE, dt);
            cur.breath = approach(cur.breath, reduced ? 0 : target.breath, PARAM_RATE, dt);
            cur.spin = approach(cur.spin, target.spin, PARAM_RATE, dt);
            cur.ripple = approach(cur.ripple, reduced ? 0 : target.ripple, PARAM_RATE, dt);
            var rgb = palette[target.color] || FALLBACK_RGB;
            cur.rgb = [
                approach(cur.rgb[0], rgb[0], COLOUR_RATE, dt),
                approach(cur.rgb[1], rgb[1], COLOUR_RATE, dt),
                approach(cur.rgb[2], rgb[2], COLOUR_RATE, dt)
            ];
            var measured = readLevel(target.source);
            previousLevel = level;
            level = approach(level, measured, measured > level ? 16 : 5, dt);
            phase += dt * (0.6 + cur.spin * 4);
            if (!reduced) {
                stepTicks(dt);
                maybeRipple();
            }
        }

        function stepTicks(dt) {
            var live = readSpectrum(target.source);
            for (var i = 0; i < TICKS; i++) {
                var wanted = live ? spectrum[i] * cur.ticks : 0;
                var rate = wanted > tickValues[i] ? 18 : 6;
                tickValues[i] = approach(tickValues[i], wanted, rate, dt);
            }
        }

        function maybeRipple() {
            if (cur.ripple < 0.3 || level - previousLevel < 0.08) {
                return;
            }
            if (clock - lastRipple < RIPPLE_GAP) {
                return;
            }
            lastRipple = clock;
            rippleBirth[rippleSlot] = clock;
            rippleSlot = (rippleSlot + 1) % RIPPLES;
        }

        function drawHalo(cx, cy, radius) {
            colourText = rgbString(cur.rgb);
            ctx.fillStyle = colourText;
            for (var i = 0; i < HALO; i++) {
                ctx.globalAlpha = cur.glow * (0.05 + 0.035 * (HALO - i));
                ctx.beginPath();
                ctx.arc(cx, cy, radius * (1.95 - 0.18 * i), 0, TWO_PI);
                ctx.fill();
            }
        }

        function drawRings(cx, cy, radius) {
            ctx.strokeStyle = colourText;
            ctx.lineWidth = 1;
            for (var k = 0; k < RINGS; k++) {
                var drift = Math.sin(phase * (0.5 + k * 0.23) + k * 1.9) * 0.05;
                ctx.globalAlpha = 0.16 - k * 0.03 + cur.glow * 0.1;
                ctx.beginPath();
                ctx.arc(cx, cy, radius * (1.42 + 0.26 * k + drift), 0, TWO_PI);
                ctx.stroke();
            }
        }

        function drawTicks(cx, cy, radius) {
            if (cur.ticks < 0.01) {
                return;
            }
            ctx.strokeStyle = colourText;
            ctx.lineWidth = 1.6;
            ctx.globalAlpha = 0.5 + 0.4 * cur.ticks;
            ctx.beginPath();
            for (var i = 0; i < TICKS; i++) {
                var angle = i / TICKS * TWO_PI - Math.PI / 2;
                var inner = radius * 1.2;
                var length = radius * (0.02 + 0.3 * tickValues[i]);
                var cos = Math.cos(angle);
                var sin = Math.sin(angle);
                ctx.moveTo(cx + cos * inner, cy + sin * inner);
                ctx.lineTo(cx + cos * (inner + length), cy + sin * (inner + length));
            }
            ctx.stroke();
        }

        function drawComet(cx, cy, radius) {
            if (cur.comet < 0.01) {
                return;
            }
            var segments = 36;
            var sweep = 1.5;
            var head = phase * 1.1;
            ctx.strokeStyle = colourText;
            for (var s = 0; s < segments; s++) {
                var t = s / segments;
                var a0 = head - sweep * (1 - t);
                var a1 = head - sweep * (1 - (t + 1 / segments));
                ctx.globalAlpha = cur.comet * Math.pow(t, 2.2) * 0.9;
                ctx.lineWidth = 1.5 + 2.5 * t;
                ctx.beginPath();
                ctx.arc(cx, cy, radius * 1.36, a0, a1);
                ctx.stroke();
            }
        }

        function drawWaves(cx, cy, radius) {
            if (cur.waves < 0.01) {
                return;
            }
            ctx.strokeStyle = colourText;
            var strength = cur.waves * (0.3 + 0.7 * level);
            for (var k = 0; k < WAVES; k++) {
                var p = (phase * 0.16 + k / WAVES) % 1;
                ctx.globalAlpha = strength * Math.pow(1 - p, 1.6) * 0.8;
                ctx.lineWidth = 1 + 2.5 * level;
                ctx.beginPath();
                ctx.arc(cx, cy, radius * (1.0 + 0.95 * p), 0, TWO_PI);
                ctx.stroke();
            }
        }

        function drawRipples(cx, cy, radius) {
            if (cur.ripple < 0.01) {
                return;
            }
            ctx.strokeStyle = colourText;
            ctx.lineWidth = 1.5;
            for (var r = 0; r < RIPPLES; r++) {
                var age = clock - rippleBirth[r];
                if (rippleBirth[r] === 0 || age < 0 || age > RIPPLE_LIFE) {
                    continue;
                }
                var p = age / RIPPLE_LIFE;
                ctx.globalAlpha = cur.ripple * (1 - p) * 0.55;
                ctx.beginPath();
                ctx.arc(cx, cy, radius * (1.1 + 0.95 * p), 0, TWO_PI);
                ctx.stroke();
            }
        }

        function drawCore(cx, cy, radius) {
            var lit = lighten(cur.rgb, 0.12 + 0.22 * cur.ripple);
            litText = rgbString(lit);
            ctx.fillStyle = colourText;
            ctx.globalAlpha = 0.12;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 1.22, 0, TWO_PI);
            ctx.fill();
            ctx.globalAlpha = 0.2;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 1.1, 0, TWO_PI);
            ctx.fill();
            ctx.globalAlpha = 0.42;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 0.97, 0, TWO_PI);
            ctx.fill();
            ctx.fillStyle = litText;
            ctx.globalAlpha = 0.78;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 0.8, 0, TWO_PI);
            ctx.fill();
            ctx.fillStyle = "rgb(255,255,255)";
            ctx.globalAlpha = 0.16;
            ctx.beginPath();
            ctx.arc(cx - radius * 0.22, cy - radius * 0.26, radius * 0.4, 0, TWO_PI);
            ctx.fill();
            ctx.globalAlpha = 0.3;
            ctx.beginPath();
            ctx.arc(cx - radius * 0.3, cy - radius * 0.34, radius * 0.14, 0, TWO_PI);
            ctx.fill();
        }

        function radiusNow() {
            var base = Math.min(width, height) * 0.18;
            var breathing = cur.breath * Math.sin(phase * 0.8);
            var swell = 0.14 * level;
            return base * (cur.core + breathing + swell);
        }

        function drawMotion() {
            var cx = width / 2;
            var cy = height / 2;
            var radius = radiusNow();
            ctx.clearRect(0, 0, width, height);
            drawHalo(cx, cy, radius);
            drawRings(cx, cy, radius);
            drawTicks(cx, cy, radius);
            drawComet(cx, cy, radius);
            drawWaves(cx, cy, radius);
            drawRipples(cx, cy, radius);
            drawCore(cx, cy, radius);
            ctx.globalAlpha = 1;
        }

        function drawStatic() {
            var cx = width / 2;
            var cy = height / 2;
            var radius = radiusNow();
            ctx.clearRect(0, 0, width, height);
            colourText = rgbString(cur.rgb);
            ctx.fillStyle = colourText;
            ctx.globalAlpha = 0.14 + 0.2 * level;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 1.5, 0, TWO_PI);
            ctx.fill();
            ctx.globalAlpha = 0.9;
            ctx.beginPath();
            ctx.arc(cx, cy, radius, 0, TWO_PI);
            ctx.fill();
            ctx.strokeStyle = colourText;
            ctx.lineWidth = 2 + 12 * level;
            ctx.globalAlpha = 0.35 + 0.5 * level;
            ctx.beginPath();
            ctx.arc(cx, cy, radius * 1.3, 0, TWO_PI);
            ctx.stroke();
            ctx.globalAlpha = 1;
        }

        function draw(now) {
            if (width === 0 || height === 0) {
                return;
            }
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            if (reduced) {
                if (now - lastStatic < 200) {
                    return;
                }
                lastStatic = now;
                drawStatic();
            } else {
                drawMotion();
            }
        }

        function frame(now) {
            if (!running) {
                return;
            }
            if (document.hidden) {
                running = false;
                return;
            }
            if (lastFrame && now - lastFrame < FRAME_MS) {
                window.requestAnimationFrame(frame);
                return;
            }
            var dt = lastFrame ? Math.min(0.1, (now - lastFrame) / 1000) : 1 / 60;
            lastFrame = now;
            step(dt);
            draw(now);
            window.requestAnimationFrame(frame);
        }

        function start() {
            if (running) {
                return;
            }
            running = true;
            lastFrame = 0;
            window.requestAnimationFrame(frame);
        }

        function setTarget(name) {
            target = STATES[name] || STATES.idle;
        }

        function setAnalyser(source, analyser) {
            if (source === "input") {
                input = analyser;
            } else {
                output = analyser;
            }
            timeBuffers[source] = analyser ? new Float32Array(analyser.fftSize) : null;
            freqBuffers[source] = analyser ? new Uint8Array(analyser.frequencyBinCount) : null;
        }

        if (window.ResizeObserver) {
            new window.ResizeObserver(measure).observe(canvas);
        }
        measure();
        document.addEventListener("visibilitychange", function () {
            if (!document.hidden) {
                start();
            }
        });
        if (darkQuery && darkQuery.addEventListener) {
            darkQuery.addEventListener("change", function () {
                readPalette(palette);
            });
        }
        if (reducedQuery && reducedQuery.addEventListener) {
            reducedQuery.addEventListener("change", function () {
                reduced = reducedQuery.matches;
            });
        }
        start();

        return {
            setState: setTarget,
            setInput: function (analyser) {
                setAnalyser("input", analyser);
            },
            setOutput: function (analyser) {
                setAnalyser("output", analyser);
            }
        };
    }

    window.QlawkusVoiceOrb = { create: create };
})();
