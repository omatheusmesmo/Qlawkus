import http from "node:http";
import { pathToFileURL } from "node:url";

const port = Number(process.env.FAKE_PORT ?? 19999);

const REPLY = [
    "Claro, eu posso ajudar você com isso.",
    " Hoje o tempo está firme e também agradável em toda a cidade.",
    " Amanhã deve chover um pouco à tarde, então leve um guarda-chuva."
];

const UTTERANCES = [
    { text: "me fala sobre o tempo de hoje", language: "portuguese" },
    { text: "e amanha", language: "portuguese" }
];

let utterance = 0;

export function wav(samples, rate) {
    const data = Buffer.alloc(samples.length * 2);
    samples.forEach((value, i) => data.writeInt16LE(Math.round(value * 0x7fff), i * 2));
    const header = Buffer.alloc(44);
    header.write("RIFF", 0);
    header.writeUInt32LE(36 + data.length, 4);
    header.write("WAVE", 8);
    header.write("fmt ", 12);
    header.writeUInt32LE(16, 16);
    header.writeUInt16LE(1, 20);
    header.writeUInt16LE(1, 22);
    header.writeUInt32LE(rate, 24);
    header.writeUInt32LE(rate * 2, 28);
    header.writeUInt16LE(2, 32);
    header.writeUInt16LE(16, 34);
    header.write("data", 36);
    header.writeUInt32LE(data.length, 40);
    return Buffer.concat([header, data]);
}

export function captureClip() {
    const rate = 48000;
    const samples = new Float32Array(Math.round(rate * 13.5));
    const toneStart = rate * 3;
    const toneEnd = rate * 4.5;
    for (let i = toneStart; i < toneEnd; i++) {
        samples[i] = 0.5 * Math.sin((2 * Math.PI * 220 * i) / rate);
    }
    return wav(samples, rate);
}

export function speechClip() {
    const rate = 24000;
    const samples = new Float32Array(rate * 1.6);
    for (let i = 0; i < samples.length; i++) {
        samples[i] = 0.3 * Math.sin((2 * Math.PI * 330 * i) / rate);
    }
    return wav(samples, rate);
}

function readBody(request) {
    return new Promise((resolve) => {
        const chunks = [];
        request.on("data", (chunk) => chunks.push(chunk));
        request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    });
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function embedding(size) {
    return Array.from({ length: size }, () => 0.01);
}

export function createServer() {
    const clip = speechClip();
    return http.createServer(async (request, response) => {
        const body = await readBody(request);
        const path = request.url ?? "";
        if (process.env.FAKE_LOG) {
            console.log(request.method, path);
        }
        if (request.method === "POST" && path === "/v1/audio/transcriptions") {
            await sleep(700);
            const heard = UTTERANCES[utterance % UTTERANCES.length];
            utterance++;
            const verbose = body.includes("verbose_json");
            response.writeHead(200, { "Content-Type": "application/json" });
            response.end(JSON.stringify(verbose ? heard : { text: heard.text }));
            return;
        }
        if (request.method === "POST" && path === "/api/chat") {
            await sleep(1500);
            response.writeHead(200, { "Content-Type": "application/x-ndjson" });
            const ts = "2026-04-25T00:00:01Z";
            for (const part of REPLY) {
                response.write(JSON.stringify({ model: "fake", created_at: ts, message: { role: "assistant", content: part }, done: false }) + "\n");
                await sleep(350);
            }
            response.end(JSON.stringify({ model: "fake", created_at: ts, message: { role: "assistant", content: "" }, done: true, done_reason: "stop", prompt_eval_count: 9, eval_count: 4 }) + "\n");
            return;
        }
        if (request.method === "POST" && path === "/v1/chat/completions") {
            await sleep(1500);
            const id = "chatcmpl-fake";
            const created = Math.floor(Date.now() / 1000);
            const streaming = body.includes('"stream":true');
            if (!streaming) {
                response.writeHead(200, { "Content-Type": "application/json" });
                response.end(JSON.stringify({ id, object: "chat.completion", created, model: "fake", choices: [{ index: 0, message: { role: "assistant", content: REPLY.join("") }, finish_reason: "stop" }], usage: { prompt_tokens: 9, completion_tokens: 4, total_tokens: 13 } }));
                return;
            }
            response.writeHead(200, { "Content-Type": "text/event-stream" });
            for (const part of REPLY) {
                response.write("data: " + JSON.stringify({ id, object: "chat.completion.chunk", created, model: "fake", choices: [{ index: 0, delta: { role: "assistant", content: part }, finish_reason: null }] }) + "\n\n");
                await sleep(350);
            }
            response.write("data: " + JSON.stringify({ id, object: "chat.completion.chunk", created, model: "fake", choices: [{ index: 0, delta: {}, finish_reason: "stop" }], usage: { prompt_tokens: 9, completion_tokens: 4, total_tokens: 13 } }) + "\n\n");
            response.end("data: [DONE]\n\n");
            return;
        }
        if (request.method === "POST" && path === "/api/embed") {
            response.writeHead(200, { "Content-Type": "application/json" });
            response.end(JSON.stringify({ model: "nomic-embed-text", embeddings: [embedding(768)], total_duration: 0 }));
            return;
        }
        if (request.method === "POST" && path === "/v1/embeddings") {
            response.writeHead(200, { "Content-Type": "application/json" });
            response.end(JSON.stringify({ data: [{ embedding: embedding(1024), index: 0 }], usage: { prompt_tokens: 1, total_tokens: 1 } }));
            return;
        }
        if (request.method === "POST" && path === "/v1/audio/speech") {
            await sleep(500);
            response.writeHead(200, { "Content-Type": "audio/mpeg" });
            response.end(clip);
            return;
        }
        response.writeHead(404);
        response.end();
    });
}

const entry = process.argv[1] ? pathToFileURL(process.argv[1]).href : "";
if (import.meta.url === entry) {
    createServer().listen(port, () => console.log(`fake providers on http://localhost:${port}`));
}
