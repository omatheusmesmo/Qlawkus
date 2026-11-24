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

const failures = [];
const check = (condition, message) => {
    if (!condition) failures.push(message);
};

const browser = await playwright.chromium.launch({ executablePath });
const context = await browser.newContext({
    viewport: { width: 1100, height: 800 },
    httpCredentials: { username: user, password },
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));

await page.goto(`${base}/console/config`, { waitUntil: "load" });
const form = page.locator("form.cfg-row").first();
const input = form.locator("input[name=value]");
const apply = form.locator("button[type=submit]");

const region = page.locator("#qk-toasts");
check(await region.getAttribute("aria-live") === "polite", "the toast region is polite");

let release;
const gate = new Promise((resolve) => { release = resolve; });
await page.route("**/api/admin/runtime-toggles", async (route) => {
    await gate;
    await route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ error: "Value is not a valid number.", field: "value" }) });
});
await input.fill("nope");
await apply.click();
await page.waitForSelector("button.is-busy[aria-busy=true]");
check(await apply.isDisabled(), "the control is disabled while its request runs");
if (shotsDir) await page.screenshot({ path: `${shotsDir}/feedback-busy.png` });
release();

await page.waitForSelector(".toast.toast-err");
const toast = page.locator(".toast.toast-err").first();
check((await toast.textContent()).includes("Value is not a valid number."), "the toast carries the server message");
check(await toast.getAttribute("role") === "alert", "an error toast is an alert");
check(!(await apply.isDisabled()), "the control is usable again after the response");
check(await apply.getAttribute("aria-busy") === null, "the busy flag is cleared");
check(await input.getAttribute("aria-invalid") === "true", "the rejected field is flagged invalid");
const described = await input.getAttribute("aria-describedby");
check(!!described && (await page.locator("#" + described).textContent()).includes("not a valid number"), "the field points at its message");
check(await input.evaluate((node) => node === document.activeElement), "focus lands on the invalid field");
if (shotsDir) await page.screenshot({ path: `${shotsDir}/feedback-invalid.png` });

await input.fill("12");
check(await input.getAttribute("aria-invalid") === null, "editing the field clears the invalid state");

await page.unroute("**/api/admin/runtime-toggles");
await page.route("**/api/admin/runtime-toggles", (route) => route.fulfill({ status: 204 }));
await apply.click();
await page.waitForSelector(".toast.toast-ok");
check((await page.locator(".toast.toast-ok").first().textContent()).startsWith("Saved"), "a success toast names what was saved");
const before = await page.locator(".toast").count();
await page.locator(".toast .toast-close").first().click();
check(await page.locator(".toast").count() === before - 1, "a toast can be dismissed");

check(errors.length === 0, "page errors: " + errors.join("; "));
await browser.close();
if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log("feedback-check: ok");
