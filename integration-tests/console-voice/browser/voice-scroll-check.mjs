// Run with agent-browser on PATH: node integration-tests/console-voice/browser/voice-scroll-check.mjs
// Uses the real template, stylesheet and script, with a local WebSocket fixture; no model or credentials.
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile, mkdir } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const run = promisify(execFile);
const root = resolve(fileURLToPath(new URL("../../../", import.meta.url)));
const session = "voice-scroll-" + process.pid;
const shots = process.env.SHOTS_DIR || join(tmpdir(), session);
await mkdir(shots, { recursive: true });
const fixture = `<script>
const reduced = new URLSearchParams(location.search).has('reduced');
const match = window.matchMedia.bind(window);
window.matchMedia = query => {const media = match(query); if (query === '(prefers-reduced-motion: reduce)') Object.defineProperty(media, 'matches', {value: reduced}); return media;};
navigator.mediaDevices.getUserMedia = () => Promise.reject({name: 'NotAllowedError'});
window.WebSocket = class {
  static OPEN = 1;
  readyState = 1;
  constructor() {
    window.fixtureReceive = event => this.onmessage({data: JSON.stringify(event)});
    setTimeout(() => window.fixtureReceive({type: 'ready', languages: ['en'], language: 'auto', maxSessionSeconds: 1800, speechBuffered: true, speech: 'fixture'}), 10);
  }
  send() {}
  close() {}
};
</script>`;
const voice = await readFile(join(root, "console-voice/runtime/src/main/resources/templates/console/voice.html"), "utf8");
const body = voice.slice(voice.indexOf("<header"), voice.lastIndexOf("{/include}"))
    .replaceAll("{socketPath}", "/voice-fixture").replaceAll("{hangoverMs}", "800")
    .replaceAll("{speechThreshold}", "0.02").replaceAll("{maxUtteranceMs}", "30000").replaceAll("{maxTextChars}", "2000");
const base = await readFile(join(root, "console/runtime/src/main/resources/templates/console/base.html"), "utf8");
const html = base.replace("{#insert /}", body)
    .replace("{#insert head}{/insert}", fixture + voice.match(/\{#head\}([\s\S]*?)\{\/head\}/)[1])
    .replaceAll(/\{#if active == '[^']+'\} is-active\{\/if\}/g, "")
    .replaceAll(/\{#[^}]+\}|\{\/[^}]+\}/g, "");
const assets = {
    "/console/app.css": "console/runtime/src/main/resources/META-INF/resources/console/app.css",
    "/console/console.js": "console/runtime/src/main/resources/META-INF/resources/console/console.js",
    "/console/htmx.min.js": "console/runtime/src/main/resources/META-INF/resources/console/htmx.min.js",
    "/console/voice.js": "console-voice/runtime/src/main/resources/META-INF/resources/console/voice.js",
    "/console/voice-orb.js": "console-voice/runtime/src/main/resources/META-INF/resources/console/voice-orb.js"
};
const server = http.createServer(async (request, response) => {
    const path = new URL(request.url, "http://localhost").pathname;
    response.setHeader("Content-Type", path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html");
    response.end(assets[path] ? await readFile(join(root, assets[path])) : html);
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const url = "http://127.0.0.1:" + server.address().port;

async function browser(...args) {
    const { stdout } = await run("rtk", ["proxy", "agent-browser", "--session", session, ...args], { maxBuffer: 2e6 });
    return stdout;
}
async function evaluate(code) {
    const value = JSON.parse(await browser("--json", "eval", "-b", Buffer.from(code).toString("base64")));
    assert.equal(value.success, true, JSON.stringify(value));
    return value.data.result;
}

try {
    for (const viewport of [{name: "desktop", width: 1280, height: 860}, {name: "phone", width: 390, height: 844}]) {
        await browser("set", "viewport", String(viewport.width), String(viewport.height));
        for (const reduced of [false, true]) {
            const label = viewport.name + (reduced ? "-reduced" : "");
            await browser("open", url + (reduced ? "?reduced=1" : ""));
            const empty = await evaluate(`(() => {const c = document.querySelector('.voice-conversation'); const l = document.querySelector('#voice-log'); return {height:c.clientHeight, empty:!l.children.length, focusable:l.tabIndex===0};})()`);
            assert.equal(empty.empty, true, label + " empty conversation");
            assert.equal(empty.focusable, true, label + " keyboard focus");
            await browser("screenshot", join(shots, label + "-empty.png"));
            await browser("focus", "#voice-toggle");
            await browser("press", "Enter");
            await browser("wait", "#voice-text:enabled");
            const filled = await evaluate(`(() => {for(let i=1;i<=50;i++) fixtureReceive({type:'transcript',turn:i,text:'Earlier message '+i+' '+('conversation '.repeat(14)),typed:true}); const l=document.querySelector('#voice-log'); return {height:document.querySelector('.voice-conversation').clientHeight,overflow:l.scrollHeight>l.clientHeight,bottom:l.scrollHeight-l.clientHeight-l.scrollTop};})()`);
            assert.equal(filled.height, empty.height, label + " bounded conversation height");
            assert.equal(filled.overflow, true, label + " own scroll overflow");
            assert.ok(filled.bottom <= 2, label + " append follows latest");
            await evaluate(`document.querySelector('#voice-log').scrollTop=120; true`);
            const preserved = await evaluate(`(() => {const l=document.querySelector('#voice-log');const before=l.scrollTop;fixtureReceive({type:'transcript',turn:51,text:'New message while reading history',typed:true});fixtureReceive({type:'text',turn:51,chunk:'${"Streamed reply. ".repeat(40)}'}); return {before,after:l.scrollTop};})()`);
            assert.equal(preserved.after, preserved.before, label + " preserve position on append");
            await browser("wait", "600");
            assert.equal(await evaluate("document.querySelector('#voice-log').scrollTop"), preserved.before, label + " preserve position during streaming");
            await evaluate(`fixtureReceive({type:'interrupted',turn:51}); true`);
            assert.equal(await evaluate("document.querySelector('#voice-log').scrollTop"), preserved.before, label + " preserve position when buffered text flushes");
            await evaluate(`const l=document.querySelector('#voice-log');l.scrollTop=l.scrollHeight;fixtureReceive({type:'text',turn:52,chunk:'${"Latest reply. ".repeat(40)}'}); true`);
            await browser("wait", "600");
            assert.ok(await evaluate("(() => {const l=document.querySelector('#voice-log');return l.scrollHeight-l.clientHeight-l.scrollTop;})()") <= 2, label + " resume following after returning to bottom");
            const keyboardStart = await evaluate("document.querySelector('#voice-log').scrollTop");
            await browser("focus", "#voice-log");
            await browser("press", "Home");
            await browser("press", "PageUp");
            await browser("wait", "200");
            assert.ok(await evaluate("document.querySelector('#voice-log').scrollTop") < keyboardStart, label + " keyboard scroll");
            await browser("screenshot", join(shots, label + "-conversation.png"));
            console.log("PASS " + label + ": bounded height, append/stream/flush scroll preservation, follow resumption and keyboard access");
        }
    }
    console.log("Screenshots: " + shots);
} catch (error) {
    console.error(await evaluate("({state:document.querySelector('#voice-state')?.textContent,error:document.querySelector('#voice-error')?.textContent,connected:typeof window.fixtureReceive})"));
    console.error(await browser("errors"));
    throw error;
} finally {
    await browser("close").catch(() => {});
    await new Promise(resolve => server.close(resolve));
}
