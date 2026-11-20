import { pathToFileURL } from "node:url";

const base = process.env.BASE_URL ?? "http://localhost:8080";
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;

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

const browser = await playwright.chromium.launch({
    executablePath,
    args: ["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream"],
});

const failures = [];
try {
    const context = await browser.newContext({
        httpCredentials: { username: user, password },
        permissions: ["microphone"],
    });
    const page = await context.newPage();
    const frames = [];
    page.on("websocket", (socket) => {
        socket.on("framereceived", (frame) => {
            if (typeof frame.payload === "string") {
                frames.push(frame.payload);
            }
        });
    });

    await page.goto(`${base}/console/voice`);
    const toggle = page.locator("#voice-toggle");
    await toggle.click();
    try {
        await page.waitForFunction(
            () => document.getElementById("voice-toggle")?.textContent?.trim() === "Stop microphone",
            null,
            { timeout: 10000 },
        );
    } catch {
        failures.push("the toggle did not switch to Stop microphone");
    }

    const deadline = Date.now() + 20000;
    while (Date.now() < deadline && !frames.some((payload) => payload.includes('"type":"ready"'))) {
        await page.waitForTimeout(200);
    }
    if (!frames.some((payload) => payload.includes('"type":"ready"'))) {
        failures.push("the voice socket never reported ready");
    }

    await toggle.click();
    try {
        await page.waitForFunction(
            () => document.getElementById("voice-status")?.textContent?.trim() === "Idle",
            null,
            { timeout: 10000 },
        );
    } catch {
        failures.push("stopping did not return the status to Idle");
    }
} finally {
    await browser.close();
}

if (failures.length > 0) {
    console.error("FAIL:\n- " + failures.join("\n- "));
    process.exit(1);
}
console.log("PASS: voice page starts the microphone, the socket reports ready, and stop returns to Idle");
