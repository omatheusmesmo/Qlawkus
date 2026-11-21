import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { captureClip } from "./fake-providers.mjs";

const base = process.env.BASE_URL ?? "http://localhost:8080";
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;
const shotsDir = process.env.SHOTS_DIR || "";

let playwright;
try {
    playwright = await import(playwrightEntry ? pathToFileURL(playwrightEntry).href : "playwright");
} catch {
    console.log("SKIP: playwright is not importable (set PLAYWRIGHT_INDEX to its index.mjs)");
    process.exit(0);
}

if (!password) {
    console.error("QLAWKUS_PASSWORD is required");
    process.exit(1);
}

const captureFile = join(tmpdir(), `qlawkus-voice-capture-${process.pid}.wav`);
writeFileSync(captureFile, captureClip());

const VIEWPORTS = [
    { name: "desktop", width: 1280, height: 860, mobile: false },
    { name: "phone", width: 390, height: 844, mobile: true },
];
const SCHEMES = ["light", "dark"];

const browser = await playwright.chromium.launch({
    executablePath,
    args: [
        "--use-fake-ui-for-media-stream",
        "--use-fake-device-for-media-stream",
        `--use-file-for-fake-audio-capture=${captureFile}`,
        "--autoplay-policy=no-user-gesture-required",
    ],
});

const failures = [];
const shots = [];

function check(condition, message, scenario) {
    if (!condition) {
        failures.push(`[${scenario}] ${message}`);
    }
}

async function waitFor(predicate, timeout, label, scenario) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        if (predicate()) {
            return true;
        }
        await new Promise((resolve) => setTimeout(resolve, 100));
    }
    failures.push(`[${scenario}] timed out waiting for ${label}`);
    return false;
}

async function waitForState(page, state, timeout, scenario) {
    try {
        await page.waitForFunction(
            (expected) => document.getElementById("voice-state")?.getAttribute("data-state") === expected,
            state,
            { timeout, polling: 100 },
        );
        return true;
    } catch {
        failures.push(`[${scenario}] the page never reached the ${state} state`);
        return false;
    }
}

async function shot(page, viewport, scheme, name) {
    if (!shotsDir) {
        return;
    }
    mkdirSync(shotsDir, { recursive: true });
    const path = join(shotsDir, `${viewport.name}-${scheme}-${name}.png`);
    await page.waitForTimeout(700);
    await page.screenshot({ path, fullPage: true });
    shots.push(path);
}

function parseFrames(frames) {
    return frames.map((payload) => {
        try {
            return JSON.parse(payload);
        } catch {
            return null;
        }
    }).filter(Boolean);
}

async function scenario(viewport, scheme) {
    const label = `${viewport.name}/${scheme}`;
    const context = await browser.newContext({
        httpCredentials: { username: user, password },
        permissions: ["microphone"],
        viewport: { width: viewport.width, height: viewport.height },
        isMobile: viewport.mobile,
        hasTouch: viewport.mobile,
        colorScheme: scheme,
    });
    const page = await context.newPage();
    const frames = [];
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    page.on("websocket", (socket) => {
        socket.on("framereceived", (frame) => {
            if (typeof frame.payload === "string") {
                frames.push(frame.payload);
            }
        });
    });

    try {
        await page.goto(`${base}/console/voice`);
        check(await page.locator("#voice-state").innerText() === "Idle", "the page does not start Idle", label);
        await shot(page, viewport, scheme, "idle");

        await page.locator("#voice-toggle").click();
        await waitFor(() => parseFrames(frames).some((e) => e.type === "ready"), 20000, "the ready event", label);
        const ready = parseFrames(frames).find((e) => e.type === "ready");
        check(ready && ready.language === "auto", "the session does not start in auto language mode", label);
        check(ready && JSON.stringify(ready.languages) === JSON.stringify(["en", "es", "pt"]),
            `the ready event does not offer the speakable languages: ${JSON.stringify(ready?.languages)}`, label);

        if (await waitForState(page, "listening", 10000, label)) {
            await shot(page, viewport, scheme, "listening");
        }
        if (await waitForState(page, "hearing", 15000, label)) {
            await shot(page, viewport, scheme, "hearing");
        }
        if (await waitForState(page, "thinking", 15000, label)) {
            await shot(page, viewport, scheme, "thinking");
        }
        if (await waitForState(page, "speaking", 25000, label)) {
            await shot(page, viewport, scheme, "speaking");
        }

        const transcript = parseFrames(frames).find((e) => e.type === "transcript" && !e.typed);
        check(transcript && transcript.language === "pt", "the detected language did not come back with the transcript", label);
        check(parseFrames(frames).some((e) => e.type === "audio" && e.language === "pt"),
            "the reply was not spoken in Portuguese", label);
        const replying = await page.locator("#voice-replying").innerText();
        check(/Portuguese/.test(replying), `the page does not show the reply language: "${replying}"`, label);

        try {
            await page.waitForFunction(
                () => document.getElementById("voice-interrupt")?.disabled === false,
                null,
                { timeout: 10000, polling: 100 },
            );
            await page.locator("#voice-interrupt").click();
        } catch {
            failures.push(`[${label}] the Interrupt button never became enabled while the reply played`);
        }
        await waitFor(() => parseFrames(frames).some((e) => e.type === "interrupted"), 5000, "the interrupted event", label);
        try {
            await page.waitForFunction(
                () => ["listening", "hearing"].includes(document.getElementById("voice-state")?.getAttribute("data-state")),
                null,
                { timeout: 12000, polling: 100 },
            );
        } catch {
            failures.push(`[${label}] after the interrupt the page did not settle back to listening or hearing`);
        }

        await page.locator("#voice-text").fill("obrigado pela ajuda");
        await page.locator("#voice-send").click();
        await waitFor(() => parseFrames(frames).some((e) => e.type === "transcript" && e.typed === true),
            5000, "the typed transcript", label);

        await page.locator("#voice-toggle").click();
        await waitForState(page, "idle", 10000, label);
        check(await page.locator("#voice-toggle").getAttribute("aria-label") === "Start microphone", "stop did not reset the toggle", label);
        check(pageErrors.length === 0, `page errors: ${pageErrors.join("; ")}`, label);
    } finally {
        await context.close();
    }
}

try {
    for (const viewport of VIEWPORTS) {
        for (const scheme of SCHEMES) {
            await scenario(viewport, scheme);
        }
    }
} finally {
    await browser.close();
    rmSync(captureFile, { force: true });
}

if (shots.length > 0) {
    console.log("Screenshots:\n" + shots.map((path) => `- ${path}`).join("\n"));
}
if (failures.length > 0) {
    console.error("FAIL:\n- " + failures.join("\n- "));
    process.exit(1);
}
console.log("PASS: voice page starts the microphone, the socket reports ready with auto language, a Portuguese utterance is transcribed with its language and answered in Portuguese, the reply can be interrupted, a typed message is accepted, and stop returns to Idle");
