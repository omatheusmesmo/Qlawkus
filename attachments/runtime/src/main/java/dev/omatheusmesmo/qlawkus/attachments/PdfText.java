package dev.omatheusmesmo.qlawkus.attachments;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import java.io.IOException;
import java.io.Writer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.text.PDFTextStripper;

final class PdfText {
    static final int MAX_PAGES = 20;
    static final int MAX_TEXT = 12_000;
    private static final long MAX_SCRATCH_BYTES = 16 * 1024 * 1024;

    private PdfText() {
    }

    static String extract(byte[] bytes) {
        try (var source = new RandomAccessReadBuffer(bytes);
                var pdf = Loader.loadPDF(source,
                        () -> new ScratchFile(MemoryUsageSetting.setupMainMemoryOnly(MAX_SCRATCH_BYTES)))) {
            if (pdf.isEncrypted()) {
                throw new AttachmentRejected("Send a PDF without password protection.");
            }
            var stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PAGES);
            var output = new BoundedText();
            boolean clipped = pdf.getNumberOfPages() > MAX_PAGES;
            try {
                stripper.writeText(pdf, output);
            } catch (TextLimitReached limit) {
                clipped = true;
            }
            String text = output.text.toString().strip();
            if (text.isEmpty()) {
                throw new AttachmentRejected("This PDF has no extractable text. Upload a text PDF or a supported image.");
            }
            return clipped ? text + "\n[PDF text clipped to the extraction budget.]" : text;
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof AttachmentRejected rejected) throw rejected;
            throw new AttachmentRejected("Could not read this PDF. Check that the file is valid and unencrypted.");
        }
    }

    private static final class TextLimitReached extends IOException {
    }

    private static final class BoundedText extends Writer {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            int retained = Math.min(length, MAX_TEXT - text.length());
            text.append(chars, offset, retained);
            if (retained < length) throw new TextLimitReached();
        }

        @Override public void flush() {
        }

        @Override public void close() {
        }
    }
}
