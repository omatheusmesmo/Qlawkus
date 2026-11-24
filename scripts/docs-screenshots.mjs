import { mkdir } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

/*
 * Regenerates the console screenshots used by site/content/console.adoc, one light and one dark image
 * per page the console sidebar serves. Not run in CI: the images are committed so the docs build stays
 * deterministic. See "Regenerating the screenshots" in site/content/console.adoc for the command.
 *
 * Environment:
 *   BASE_URL            running agent with the console, default http://localhost:8080
 *   QLAWKUS_USER        default qlawkus
 *   QLAWKUS_PASSWORD    required, the admin password of that instance
 *   PLAYWRIGHT_INDEX    path to playwright's index.mjs when it is not importable as "playwright"
 *   CHROMIUM_PATH       chromium or chrome executable when the bundled browser is missing
 *   OUT_DIR             default site/public/images/console
 */
const here = dirname(fileURLToPath(import.meta.url));
const base = (process.env.BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const outDir = resolve(process.env.OUT_DIR ?? join(here, "..", "site", "public", "images", "console"));
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;

if (!password) {
    console.error("QLAWKUS_PASSWORD is required");
    process.exit(1);
}

let playwright;
try {
    playwright = await import(playwrightEntry ? pathToFileURL(playwrightEntry).href : "playwright");
} catch {
    console.error("playwright is not importable (set PLAYWRIGHT_INDEX to its index.mjs)");
    process.exit(1);
}

const PAGES = [
    ["overview", "/console"],
    ["chat", "/console/chat"],
    ["approvals", "/console/approvals"],
    ["voice", "/console/voice"],
    ["specialists", "/console/specialists"],
    ["artifacts", "/console/artifacts"],
    ["memory", "/console/memory"],
    ["skills", "/console/skills"],
    ["cognition", "/console/cognition"],
    ["config", "/console/config"],
    ["mcp", "/console/mcp"],
    ["schedule", "/console/schedule"],
];
const THEMES = ["light", "dark"];
const VIEWPORT = { width: 1280, height: 800 };

const auth = "Basic " + Buffer.from(`${user}:${password}`).toString("base64");

async function seed() {
    const put = (path, body) =>
        fetch(base + path, {
            method: "POST",
            headers: { Authorization: auth, "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    const specialist = await put("/api/admin/specialists", {
        id: "release-notes",
        instructions: "Summarize merged pull requests into user-facing release notes, grouped by area.",
        model: "DEFAULT",
        enabled: true,
    });
    console.log(`seed specialist: ${specialist.status}`);
}

await mkdir(outDir, { recursive: true });
await seed().catch((e) => console.log(`seed skipped: ${e.message}`));

const browser = await playwright.chromium.launch({ executablePath });
let written = 0;
try {
    for (const theme of THEMES) {
        const context = await browser.newContext({
            viewport: VIEWPORT,
            colorScheme: theme,
            httpCredentials: { username: user, password },
            reducedMotion: "reduce",
        });
        const page = await context.newPage();
        for (const [slug, path] of PAGES) {
            await page.goto(base + path, { waitUntil: "load" });
            await page.waitForTimeout(1500);
            await page.screenshot({ path: join(outDir, `${slug}-${theme}.png`) });
            written++;
        }
        await context.close();
    }
} finally {
    await browser.close();
}
console.log(`wrote ${written} screenshots to ${outDir}`);
