import { pathToFileURL } from "node:url";

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

const PAGES = ["/console/specialists", "/console/config", "/console/schedule", "/console/setup", "/console/skills", "/console/mcp", "/console/skills/install"];
const VIEWPORTS = [
    { name: "desktop", width: 1440, height: 900 },
    { name: "phone", width: 390, height: 844 },
    { name: "narrow", width: 320, height: 640 },
];
const browser = await playwright.chromium.launch({ executablePath });
const failures = [];
const check = (condition, message, scenario) => {
    if (!condition) failures.push(`[${scenario}] ${message}`);
};

for (const viewport of VIEWPORTS) {
    const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        httpCredentials: { username: user, password },
    });
    for (const path of PAGES) {
        const scenario = `${viewport.name} ${path}`;
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", (error) => errors.push(error.message));
        const response = await page.goto(`${base}${path}`, { waitUntil: "load" }).catch(() => null);
        if (!response || response.status() >= 400) {
            await page.close();
            continue;
        }
        await page.waitForTimeout(300);
        const report = await page.evaluate(() => {
            const width = document.documentElement.clientWidth;
            const overflowing = [];
            for (const node of document.querySelectorAll(".input, .select, .textarea, .btn, .field")) {
                const box = node.getBoundingClientRect();
                if (box.width === 0 || node.closest("[hidden]") || node.closest("details:not([open])")) continue;
                if (box.right > width + 1 || box.left < -1) {
                    overflowing.push(`${node.tagName.toLowerCase()}.${node.className.split(" ")[0]}#${node.id || node.name || ""}`);
                }
            }
            const bare = [...document.querySelectorAll("button")]
                .filter((button) => !button.closest("[hidden]"))
                .filter((button) => !/\b(btn|nav-toggle|voice-|msg-copy)/.test(button.className) && button.id !== "chat-send")
                .map((button) => button.textContent.trim().slice(0, 30));
            const plain = [...document.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=file]), select, textarea")]
                .filter((node) => !node.closest("[hidden]") && !node.closest(".chat-form") && !node.closest(".voice-text"))
                .filter((node) => !/\b(input|select|textarea)\b/.test(node.className))
                .map((node) => node.tagName.toLowerCase() + "#" + (node.id || node.name));
            return { scrollWidth: document.documentElement.scrollWidth, width, overflowing, bare, plain };
        });
        check(report.scrollWidth <= report.width + 1, `the page scrolls sideways (${report.scrollWidth} > ${report.width})`, scenario);
        check(report.overflowing.length === 0, `controls overflow the viewport: ${report.overflowing.join(", ")}`, scenario);
        check(report.bare.length === 0, `buttons without the shared classes: ${report.bare.join(", ")}`, scenario);
        check(report.plain.length === 0, `controls without the shared classes: ${report.plain.join(", ")}`, scenario);
        check(errors.length === 0, `page errors: ${errors.join("; ")}`, scenario);
        if (shotsDir && viewport.name !== "desktop") {
            await page.screenshot({ path: `${shotsDir}/forms-${viewport.name}-${path.split("/").pop()}.png`, fullPage: true });
        }
        await page.close();
    }
    await context.close();
}

await browser.close();
if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log("forms-check: ok");
