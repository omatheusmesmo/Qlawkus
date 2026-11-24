import { pathToFileURL } from "node:url";

const base = process.env.BASE_URL ?? "http://localhost:8080";
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;
const shotsDir = process.env.SHOTS_DIR || "";
const maxInitialScreens = Number(process.env.MAX_INITIAL_SCREENS ?? "6");

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

const VIEWPORTS = [
    { name: "desktop", width: 1440, height: 900 },
    { name: "laptop", width: 1100, height: 800 },
    { name: "phone", width: 390, height: 844 },
];

const browser = await playwright.chromium.launch({ executablePath });
const failures = [];

function check(condition, message, scenario) {
    if (!condition) {
        failures.push(`[${scenario}] ${message}`);
    }
}

async function open(viewport, hash = "") {
    const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        httpCredentials: { username: user, password },
    });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("response", (response) => {
        if (response.status() >= 400) {
            errors.push(`${response.status()} ${response.url()}`);
        }
    });
    page.on("console", (message) => {
        if (message.type() === "error" && !message.text().startsWith("Failed to load resource")) {
            errors.push(message.text());
        }
    });
    await page.goto(`${base}/console/config${hash}`, { waitUntil: "load" });
    await page.waitForSelector("#cfg-toolbar:not([hidden])");
    return { context, page, errors };
}

function visibleRows(page) {
    return page.$$eval(".cfg-item", (rows) => rows.filter((row) => !row.hidden && row.closest("details:not([hidden])")).length);
}

function openSections(page) {
    return page.$$eval(".cfg-section", (sections) => sections.filter((s) => s.open && !s.hidden).length);
}

function status(page) {
    return page.$eval("#cfg-status", (node) => node.textContent.trim());
}

for (const viewport of VIEWPORTS) {
    const scenario = viewport.name;
    const { context, page, errors } = await open(viewport);

    const total = await page.$$eval(".cfg-item", (rows) => rows.length);
    const sections = await page.$$eval(".cfg-section", (nodes) => nodes.length);
    const height = await page.evaluate(() => document.documentElement.scrollHeight);
    const opened = await openSections(page);
    console.log(`${scenario}: ${total} rows, ${sections} sections, ${opened} open, ${height}px tall (${(height / viewport.height).toFixed(1)} screens)`);
    check(total > 50, "the page lists every documented property", scenario);
    check(height / viewport.height <= maxInitialScreens, `initial page is ${(height / viewport.height).toFixed(1)} screens tall, over ${maxInitialScreens}`, scenario);
    check((await status(page)) === "", "no status text without a filter", scenario);
    check(!(await page.isVisible("#cfg-clear")), "Clear filters is hidden while nothing is filtered", scenario);
    const offset = await page.$eval(".cfg-section", (section) => section.querySelector(".panel-title").getBoundingClientRect().top
        - section.querySelector("summary").getBoundingClientRect().top);
    check(offset < 12, `the title shares the first row with the marker (offset ${offset}px)`, scenario);

    await page.fill("#cfg-search", "timeout");
    check(await page.isVisible("#cfg-clear"), "Clear filters shows once a filter is set", scenario);
    const afterSearch = await visibleRows(page);
    check(afterSearch > 0 && afterSearch < total, `search shows a subset (${afterSearch} of ${total})`, scenario);
    check((await status(page)).includes(`${afterSearch} of ${total}`), `status reports the count: ${await status(page)}`, scenario);
    const wrong = await page.$$eval(".cfg-item", (rows) => rows
        .filter((row) => !row.hidden)
        .filter((row) => !row.textContent.toLowerCase().includes("timeout")).length);
    check(wrong === 0, "every visible row matches the search", scenario);
    const hiddenSections = await page.$$eval(".cfg-section", (nodes) => nodes.filter((n) => n.hidden).length);
    check(hiddenSections > 0, "sections without a match are hidden", scenario);
    check((await openSections(page)) === sections - hiddenSections, "matching sections open while searching", scenario);
    check((await page.evaluate(() => location.hash)).includes("q=timeout"), "the search is in the URL hash", scenario);

    await page.fill("#cfg-search", "zzz-no-such-property");
    check(await page.$eval("#cfg-empty", (node) => !node.hidden), "an empty result says so", scenario);
    check((await visibleRows(page)) === 0, "no row stays visible without a match", scenario);

    await page.click("#cfg-clear");
    check((await visibleRows(page)) === total, "clearing restores every row", scenario);
    check((await page.$eval("#cfg-search", (node) => node.value)) === "", "clearing empties the search box", scenario);
    check((await openSections(page)) === opened, "clearing restores the initial open sections", scenario);

    await page.check("#cfg-changed");
    const changedRows = await visibleRows(page);
    const changedMarked = await page.$$eval('.cfg-item[data-changed="true"]', (rows) => rows.length);
    check(changedRows === changedMarked, `changed filter shows exactly the changed rows (${changedRows} vs ${changedMarked})`, scenario);
    await page.uncheck("#cfg-changed");

    await page.selectOption("#cfg-phase", "build");
    const buildRows = await visibleRows(page);
    const buildMarked = await page.$$eval('.cfg-item[data-phase="build"]', (rows) => rows.length);
    check(buildRows === buildMarked && buildRows > 0, `build filter shows the build-time rows (${buildRows} vs ${buildMarked})`, scenario);
    await page.selectOption("#cfg-phase", "run");
    const runRows = await visibleRows(page);
    check(runRows + buildMarked === total, "run and build rows partition the page", scenario);
    await page.selectOption("#cfg-phase", "");

    const firstId = await page.$eval(".cfg-section", (node) => node.id);
    const lastId = await page.$eval(".cfg-section:last-of-type", (node) => node.id);
    if (viewport.width >= 1280) {
        await page.click(`.cfg-index-list a[data-section="${lastId}"]`);
    } else {
        await page.selectOption("#cfg-jump", lastId);
    }
    check(await page.$eval(`#${lastId}`, (node) => node.open), "jumping to a section opens it", scenario);
    check((await page.evaluate(() => location.hash)).includes("open="), "the opened sections are in the hash", scenario);

    check(errors.length === 0, `no page or console errors: ${errors.join(" | ")}`, scenario);
    if (shotsDir) {
        await page.screenshot({ path: `${shotsDir}/config-${scenario}.png` });
    }
    await context.close();
    void firstId;
}

{
    const scenario = "hash";
    const { context, page } = await open(VIEWPORTS[0], "#q=timeout&phase=run");
    check((await page.$eval("#cfg-search", (node) => node.value)) === "timeout", "the search is restored from the hash", scenario);
    check((await page.$eval("#cfg-phase", (node) => node.value)) === "run", "the phase is restored from the hash", scenario);
    check((await visibleRows(page)) > 0, "the restored filter is applied", scenario);
    await context.close();
}

{
    const scenario = "anchor";
    const probe = await open(VIEWPORTS[0]);
    const id = await probe.page.$eval(".cfg-section:nth-of-type(3)", (node) => node.id);
    await probe.context.close();
    const { context, page } = await open(VIEWPORTS[0], `#${id}`);
    check(await page.$eval(`#${id}`, (node) => node.open), "landing on #cfg-anchor opens that section", scenario);
    await context.close();
}

{
    const scenario = "shortcut";
    const { context, page } = await open(VIEWPORTS[0]);
    await page.click("h1");
    await page.keyboard.press("/");
    check(await page.evaluate(() => document.activeElement && document.activeElement.id) === "cfg-search", "/ focuses the search", scenario);
    await page.keyboard.type("memory");
    check((await page.$eval("#cfg-search", (node) => node.value)) === "memory", "the slash is not typed into the box", scenario);
    await page.keyboard.press("Escape");
    check((await page.$eval("#cfg-search", (node) => node.value)) === "", "Escape clears the search", scenario);
    await context.close();
}

{
    const scenario = "outcome";
    const { context, page } = await open(VIEWPORTS[0]);
    const property = "qlawkus.memory-curation.enabled";
    await page.fill("#cfg-search", property);
    const row = page.locator(".cfg-item", { has: page.locator(".cfg-name", { hasText: property }) }).first();
    await row.locator('input[name="value"]').fill("false");
    await row.locator('button[type="submit"]').click();
    await page.waitForFunction((name) => {
        const slot = [...document.querySelectorAll(".cfg-item")]
            .find((candidate) => candidate.querySelector(".cfg-name").textContent.trim() === name)
            .querySelector(".cfg-row-result");
        return slot.textContent.trim() !== "";
    }, property, { timeout: 10000 });
    const message = (await row.locator(".cfg-row-result").textContent()).trim();
    check(message.includes(property), `the outcome appears beside the row: ${message}`, scenario);
    const elsewhere = await page.$$eval(".cfg-row-result", (slots) => slots.filter((slot) => slot.textContent.trim() !== "").length);
    check(elsewhere === 1, `only that row shows an outcome (${elsewhere})`, scenario);
    check((await page.$eval("#cfg-result", (node) => node.textContent.trim())) === "", "the sticky banner stays empty", scenario);
    check((await row.locator(".cfg-pending").count()) === 1, "the row says the change waits for a restart", scenario);
    check((await row.getAttribute("data-changed")) === "true", "the row is marked changed", scenario);

    page.once("dialog", (dialog) => dialog.accept());
    await row.locator("button.btn-secondary").click();
    await page.waitForFunction((name) => {
        const slot = [...document.querySelectorAll(".cfg-item")]
            .find((candidate) => candidate.querySelector(".cfg-name").textContent.trim() === name)
            .querySelector(".cfg-row-result");
        return slot.textContent.includes("Removed");
    }, property, { timeout: 10000 });
    await context.close();
}

await browser.close();

if (failures.length > 0) {
    console.error(`FAIL: ${failures.length} check(s)`);
    failures.forEach((failure) => console.error(`  ${failure}`));
    process.exit(1);
}
console.log("OK: config editor browser checks passed");
