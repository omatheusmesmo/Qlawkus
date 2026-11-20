package dev.omatheusmesmo.qlawkus.tools.skillhub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds zip and tar.gz archives in memory for the archive reader and the hub tests. */
final class ArchiveFixtures {

  private ArchiveFixtures() {
  }

  record Entry(String name, byte[] content) {
  }

  static Entry file(String name, String content) {
    return new Entry(name, content.getBytes(StandardCharsets.UTF_8));
  }

  static byte[] zip(Entry... entries) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Entry entry : entries) {
        zip.putNextEntry(new ZipEntry(entry.name()));
        zip.write(entry.content());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  /** Sets the unix file mode of the first central-directory entry to a symbolic link. */
  static void markAsSymlink(byte[] zip) {
    for (int i = 0; i + 46 <= zip.length; i++) {
      if (zip[i] == 'P' && zip[i + 1] == 'K' && zip[i + 2] == 1 && zip[i + 3] == 2) {
        int mode = 0xA1FF << 16;
        zip[i + 38] = (byte) mode;
        zip[i + 39] = (byte) (mode >>> 8);
        zip[i + 40] = (byte) (mode >>> 16);
        zip[i + 41] = (byte) (mode >>> 24);
        return;
      }
    }
    throw new IllegalStateException("no central directory entry");
  }

  static byte[] tarGz(byte[]... tarEntries) throws IOException {
    ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (byte[] entry : tarEntries) {
      tar.write(entry);
    }
    tar.write(new byte[1024]);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(tar.toByteArray());
    }
    return out.toByteArray();
  }

  static byte[] tarFile(String name, String content) {
    return tarEntry(name, '0', content);
  }

  static byte[] tarEntry(String name, char type, String content) {
    byte[] body = content.getBytes(StandardCharsets.UTF_8);
    int padded = (body.length + 511) / 512 * 512;
    byte[] block = new byte[512 + padded];
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(nameBytes, 0, block, 0, Math.min(nameBytes.length, 100));
    writeOctal(block, 100, 8, 0644);
    writeOctal(block, 124, 12, type == '2' ? 0 : body.length);
    block[156] = (byte) type;
    System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, block, 257, 6);
    for (int i = 148; i < 156; i++) {
      block[i] = ' ';
    }
    long checksum = 0;
    for (byte b : block) {
      checksum += b & 0xff;
    }
    byte[] sum = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(sum, 0, block, 148, 6);
    block[154] = 0;
    block[155] = ' ';
    if (type != '2') {
      System.arraycopy(body, 0, block, 512, body.length);
    }
    return block;
  }

  private static void writeOctal(byte[] block, int offset, int length, long value) {
    byte[] digits = String.format("%0" + (length - 1) + "o", value).getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(digits, 0, block, offset, digits.length);
    block[offset + length - 1] = 0;
  }
}
