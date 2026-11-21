class VoiceCaptureProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.chunk = new Float32Array(2048);
        this.filled = 0;
    }

    process(inputs) {
        var channel = inputs[0] && inputs[0][0];
        if (!channel) {
            return true;
        }
        var offset = 0;
        while (offset < channel.length) {
            var take = Math.min(this.chunk.length - this.filled, channel.length - offset);
            this.chunk.set(channel.subarray(offset, offset + take), this.filled);
            this.filled += take;
            offset += take;
            if (this.filled === this.chunk.length) {
                this.port.postMessage(this.chunk, [this.chunk.buffer]);
                this.chunk = new Float32Array(2048);
                this.filled = 0;
            }
        }
        return true;
    }
}

registerProcessor("voice-capture", VoiceCaptureProcessor);
