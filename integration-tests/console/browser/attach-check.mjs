import { pathToFileURL } from "node:url";
import { mkdtempSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const base = process.env.BASE_URL ?? "http://localhost:8080";
const user = process.env.QLAWKUS_USER ?? "qlawkus";
const password = process.env.QLAWKUS_PASSWORD;
const executablePath = process.env.CHROMIUM_PATH || undefined;
const playwrightEntry = process.env.PLAYWRIGHT_INDEX;
const axePath = process.env.AXE_PATH;
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
const axeSource = axePath ? readFileSync(axePath, "utf8") : "";

const dir = mkdtempSync(join(tmpdir(), "attach-check-"));
const png = join(dir, "screenshot-2026-11-24-quarterly-report.png");
const pdf = join(dir, "quarterly-report.pdf");
const second = join(dir, "second.pdf");
const third = join(dir, "third.pdf");
const fourth = join(dir, "fourth.pdf");
const big = join(dir, "too-big.pdf");
const text = join(dir, "notes.txt");
writeFileSync(png, Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==", "base64"));
for (const file of [pdf, second, third, fourth]) writeFileSync(file, "%PDF-1.4\n" + file);
writeFileSync(big, Buffer.alloc(2 * 1024 * 1024 + 10, 48));
writeFileSync(text, "plain");

const VIEWPORTS = [
    { name: "desktop", width: 1280, height: 800 },
    { name: "phone", width: 390, height: 844 },
    { name: "narrow", width: 320, height: 640 },
];
const browser = await playwright.chromium.launch({ executablePath });
const failures = [];
const check = (condition, message, scenario) => {
    if (!condition) failures.push(`[${scenario}] ${message}`);
};

const axeViolations = async (page) => {
    if (!axeSource) return [];
    await page.addScriptTag({ content: axeSource });
    return page.evaluate(async () => {
        const run = await window.axe.run(document, { resultTypes: ["violations"] });
        return run.violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id);
    });
};

for (const scheme of ["light", "dark"]) {
    for (const viewport of VIEWPORTS) {
        const scenario = `${scheme} ${viewport.name}`;
        const context = await browser.newContext({
            viewport: { width: viewport.width, height: viewport.height },
            colorScheme: scheme,
            httpCredentials: { username: user, password },
        });
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", (error) => errors.push(error.message));
        await page.route("**/api/chat", async (route) => {
            await new Promise((resolve) => setTimeout(resolve, 30000));
            await route.abort();
        });
        const shot = async (name) => {
            if (shotsDir) await page.screenshot({ path: join(shotsDir, `${scheme}-${viewport.name}-${name}.png`) });
        };
        const response = await page.goto(`${base}/console/chat`, { waitUntil: "load" });
        check(response && response.status() === 200, "the chat page loads", scenario);
        await page.waitForTimeout(300);

        const attach = page.locator("#chat-attach");
        const items = page.locator("#chat-files .chat-file");
        const error = page.locator("#chat-error");
        if ((await attach.count()) === 0) {
            console.log(`SKIP: ${scenario}: the attachments extension is not on this instance`);
            await context.close();
            continue;
        }

        const idle = await page.evaluate(() => {
            const box = (selector) => {
                const node = document.querySelector(selector);
                const rect = node.getBoundingClientRect();
                return { width: rect.width, height: rect.height, right: rect.right };
            };
            const input = document.getElementById("chat-attachments");
            return {
                attach: box("#chat-attach"),
                send: box("#chat-send"),
                message: box("#chat-input"),
                nativeShown: input.offsetParent !== null,
                overflow: document.documentElement.scrollWidth - window.innerWidth,
                panelRight: document.querySelector("#chat").getBoundingClientRect().right,
            };
        });
        check(idle.attach.width >= 44 && idle.attach.height >= 44, `the attach button is at least 44px: ${idle.attach.width}x${idle.attach.height}`, scenario);
        check(idle.send.height >= 44, `Send is at least 44px tall: ${idle.send.height}`, scenario);
        check(idle.message.width >= 140, `the message box stays usable: ${idle.message.width}px`, scenario);
        check(!idle.nativeShown, "the native file input is not shown", scenario);
        check(idle.overflow <= 0, `the page does not scroll sideways: ${idle.overflow}px`, scenario);
        check(idle.send.right <= idle.panelRight + 0.5, "Send stays inside the chat panel", scenario);
        await shot("1-idle");

        await page.setInputFiles("#chat-attachments", [png, pdf]);
        check((await items.count()) === 2, "two chosen files are listed", scenario);
        const names = await page.locator("#chat-files .chat-file-name").allTextContents();
        check(names.includes("quarterly-report.pdf"), "a file shows its name", scenario);
        const label = await page.locator("#chat-files .chat-file-remove").first().getAttribute("aria-label");
        check(label && label.startsWith("Remove "), `a remove button names its file: ${label}`, scenario);
        const target = await page.locator("#chat-files .chat-file-remove").first().evaluate((node) => {
            const rect = node.getBoundingClientRect();
            return Math.min(rect.width, rect.height);
        });
        const minimum = viewport.width <= 800 ? 44 : 24;
        check(target >= minimum, `a remove target is at least ${minimum}px: ${target}`, scenario);
        const chip = await page.evaluate(() => {
            const list = document.getElementById("chat-files").getBoundingClientRect();
            const panel = document.getElementById("chat").getBoundingClientRect();
            return { right: list.right, panel: panel.right, overflow: document.documentElement.scrollWidth - window.innerWidth };
        });
        check(chip.right <= chip.panel + 0.5 && chip.overflow <= 0, "the list stays inside the panel", scenario);
        await shot("2-files");
        const violations = await axeViolations(page);
        check(violations.length === 0, `axe with files chosen: ${violations.join(", ")}`, scenario);

        await page.locator("#chat-files .chat-file-remove").first().click();
        check((await items.count()) === 1, "a file can be removed", scenario);
        const focused = await page.evaluate(() => document.activeElement && document.activeElement.className);
        check(String(focused).includes("chat-file-remove") || (await page.evaluate(() => document.activeElement.id)) === "chat-attach",
            "focus moves to a control that still exists after a removal", scenario);
        await page.locator("#chat-files .chat-file-remove").first().click();
        check((await items.count()) === 0 && (await page.locator("#chat-files").isHidden()), "the empty list is hidden", scenario);
        check((await page.evaluate(() => document.activeElement.id)) === "chat-attach", "focus returns to the attach button", scenario);

        await page.setInputFiles("#chat-attachments", [big]);
        check((await error.isVisible()) && /larger than 2\.0 MB/.test(await error.textContent()), "an oversized file is refused when chosen", scenario);
        check((await items.count()) === 0, "the oversized file is not listed", scenario);
        await shot("3-too-big");
        await page.setInputFiles("#chat-attachments", [text]);
        check(/not a PDF, PNG or JPEG/.test(await error.textContent()), "an unsupported type is refused when chosen", scenario);
        await page.setInputFiles("#chat-attachments", [png, pdf, second]);
        await page.setInputFiles("#chat-attachments", [third]);
        check((await items.count()) === 3 && /up to 3 files/.test(await error.textContent()), "a fourth file is left out with a reason", scenario);
        await page.setInputFiles("#chat-attachments", [fourth]);
        check((await items.count()) === 3, "the list never grows past three files", scenario);

        await page.locator("#chat-input").fill("summarise these");
        await page.locator("#chat-send").click();
        await page.waitForTimeout(400);
        check(await attach.isDisabled(), "the attach button is disabled while a reply is pending", scenario);
        check((await items.count()) === 0, "sending clears the list", scenario);
        await shot("4-sending");
        check(errors.length === 0, `no script errors: ${errors.join("; ")}`, scenario);
        await context.close();

        const keyboard = await browser.newContext({
            viewport: { width: viewport.width, height: viewport.height },
            colorScheme: scheme,
            httpCredentials: { username: user, password },
        });
        const kpage = await keyboard.newPage();
        await kpage.goto(`${base}/console/chat`, { waitUntil: "load" });
        await kpage.waitForTimeout(300);
        await kpage.locator("#chat-attach").focus();
        const ring = await kpage.locator("#chat-attach").evaluate((node) => getComputedStyle(node).outlineStyle);
        check(ring !== "none", "the attach button shows a focus ring", scenario);
        const chooser = kpage.waitForEvent("filechooser", { timeout: 3000 }).then(() => true, () => false);
        await kpage.keyboard.press("Enter");
        check(await chooser, "Enter on the attach button opens the file chooser", scenario);
        await keyboard.close();
    }
}

await browser.close();
if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log("attach-check: ok");
