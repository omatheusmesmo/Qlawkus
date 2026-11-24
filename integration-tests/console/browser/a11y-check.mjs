import { pathToFileURL } from "node:url";
import { readFileSync } from "node:fs";

const base = process.env.BASE_URL ?? "http://localhost:8080";
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;
const axePath = process.env.AXE_PATH;

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
if (!axePath) {
    console.error("AXE_PATH must point at axe.min.js (npm pack axe-core)");
    process.exit(1);
}
const axeSource = readFileSync(axePath, "utf8");

const PAGES = ["/console", "/console/chat", "/console/memory", "/console/skills", "/console/cognition", "/console/config",
    "/console/specialists", "/console/schedule", "/console/mcp", "/console/skills/install", "/console/setup"];
const failures = [];
const check = (condition, message) => {
    if (!condition) failures.push(message);
};

const browser = await playwright.chromium.launch({ executablePath });
const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    httpCredentials: { username: user, password },
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));

await page.goto(`${base}/console/memory`, { waitUntil: "load" });
check(await page.locator("html").getAttribute("data-theme") === null, "the first visit follows the system theme");
check(await page.locator('[data-theme-choice="system"]').getAttribute("aria-checked") === "true", "system is selected by default");

await page.locator('[data-theme-choice="dark"]').click();
check(await page.locator("html").getAttribute("data-theme") === "dark", "choosing dark applies it");
check(await page.evaluate(() => localStorage.getItem("qlawkus-theme")) === "dark", "the choice is stored");

await page.route("**/console/app.css", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    await route.continue().catch(() => {});
});
const reload = page.reload({ waitUntil: "commit" });
await page.waitForFunction(() => document.documentElement.getAttribute("data-theme") === "dark", null, { timeout: 1200 })
    .then(() => check(true), () => check(false, "the stored theme is applied while the stylesheet is still loading"));
await reload;
await page.unroute("**/console/app.css");
await page.waitForLoadState("load");
check(await page.locator('[data-theme-choice="dark"]').getAttribute("aria-checked") === "true", "the stored choice is selected after a reload");

await page.locator('[data-theme-choice="dark"]').focus();
await page.keyboard.press("ArrowLeft");
check(await page.locator("html").getAttribute("data-theme") === "light", "the arrow keys move between the choices");
check(await page.locator('[data-theme-choice="light"]').evaluate((node) => node === document.activeElement), "focus follows the selection");

await page.keyboard.press("Tab");
const first = await page.evaluate(() => {
    const active = document.activeElement;
    return active ? getComputedStyle(active).outlineStyle : "none";
});
check(first !== "none", "a focused control shows a ring");

for (const theme of ["light", "dark"]) {
    for (const path of PAGES) {
        const response = await page.goto(`${base}${path}`, { waitUntil: "load" }).catch(() => null);
        if (!response || response.status() >= 400) continue;
        await page.evaluate((value) => localStorage.setItem("qlawkus-theme", value), theme);
        await page.reload({ waitUntil: "load" });
        await page.addScriptTag({ content: axeSource });
        const result = await page.evaluate(async () => {
            const run = await window.axe.run(document, { resultTypes: ["violations"] });
            return run.violations
                .filter((violation) => violation.impact === "serious" || violation.impact === "critical")
                .map((violation) => `${violation.id} (${violation.impact}): ${violation.nodes.slice(0, 3).map((node) => node.target.join(" ")).join(" | ")}`);
        });
        for (const violation of result) check(false, `[${theme} ${path}] ${violation}`);
    }
}

check(errors.length === 0, "page errors: " + errors.join("; "));
await browser.close();
if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log("a11y-check: ok");
