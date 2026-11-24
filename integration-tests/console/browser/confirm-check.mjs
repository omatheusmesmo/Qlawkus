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

const VIEWPORTS = [
    { name: "desktop", width: 1440, height: 900 },
    { name: "phone", width: 390, height: 844 },
];
const browser = await playwright.chromium.launch({ executablePath });
const failures = [];
const check = (condition, message, scenario) => {
    if (!condition) failures.push(`[${scenario}] ${message}`);
};

async function open(viewport) {
    const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        httpCredentials: { username: user, password },
    });
    const page = await context.newPage();
    const errors = [];
    const nativeDialogs = [];
    page.on("dialog", async (dialog) => {
        nativeDialogs.push(dialog.type());
        await dialog.dismiss();
    });
    page.on("pageerror", (error) => errors.push(error.message));
    const deletes = [];
    page.on("request", (request) => {
        if (request.method() === "DELETE") deletes.push(request.url());
    });
    await page.goto(`${base}/console/memory`, { waitUntil: "load" });
    return { context, page, errors, nativeDialogs, deletes };
}

for (const viewport of VIEWPORTS) {
    const scenario = viewport.name;
    const { context, page, errors, nativeDialogs, deletes } = await open(viewport);
    const dialog = page.locator("#qk-confirm");
    const purge = page.locator("button[hx-delete*='all=true']");

    check(!(await dialog.evaluate((node) => node.open)), "the dialog starts closed", scenario);

    await purge.click();
    check(await dialog.evaluate((node) => node.open), "the purge button opens the dialog", scenario);
    check(deletes.length === 0, "nothing is sent before the owner agrees", scenario);
    check((await page.locator("#qk-confirm-target").innerText()).includes("Facts"), "the dialog names the target", scenario);
    check((await page.locator("#qk-confirm-body").innerText()).includes("cannot be undone"), "the dialog states the consequence", scenario);
    check((await page.locator("#qk-confirm-ok").innerText()) === "Purge all memory", "the confirm button carries the action", scenario);
    const focused = await page.evaluate(() => document.activeElement && document.activeElement.textContent.trim());
    check(focused === "Cancel", `Cancel takes the initial focus, got ${focused}`, scenario);

    const box = await dialog.boundingBox();
    check(box && box.x >= 0 && box.x + box.width <= viewport.width, "the dialog fits the viewport width", scenario);
    if (shotsDir) await page.screenshot({ path: `${shotsDir}/confirm-${scenario}.png` });

    await page.keyboard.press("Escape");
    check(!(await dialog.evaluate((node) => node.open)), "Escape closes the dialog", scenario);
    await page.waitForTimeout(150);
    check(deletes.length === 0, "Escape sends nothing", scenario);
    const returned = await page.evaluate(() => document.activeElement && document.activeElement.getAttribute("hx-delete"));
    check(returned && returned.includes("all=true"), "focus returns to the control that asked", scenario);

    await purge.click();
    await page.locator("#qk-confirm").click({ position: { x: 1, y: 1 } });
    check(!(await dialog.evaluate((node) => node.open)), "a click on the backdrop closes the dialog", scenario);
    await page.waitForTimeout(150);
    check(deletes.length === 0, "the backdrop click sends nothing", scenario);

    await purge.click();
    await page.getByRole("button", { name: "Cancel" }).click();
    await page.waitForTimeout(150);
    check(deletes.length === 0, "Cancel sends nothing", scenario);

    await purge.click();
    await page.locator("#qk-confirm-ok").click();
    await page.waitForTimeout(600);
    check(deletes.length === 1 && deletes[0].includes("all=true"), `confirming sends exactly one DELETE, got ${deletes.length}`, scenario);
    check(!(await dialog.evaluate((node) => node.open)), "the dialog closes after confirming", scenario);

    check(nativeDialogs.length === 0, `no native dialog appeared: ${nativeDialogs}`, scenario);
    check(errors.length === 0, `no page errors: ${errors.join("; ")}`, scenario);
    await context.close();
}

await browser.close();
if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log("confirm-check: ok");
