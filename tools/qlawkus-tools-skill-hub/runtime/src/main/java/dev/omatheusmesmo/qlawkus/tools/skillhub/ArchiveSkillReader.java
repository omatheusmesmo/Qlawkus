package dev.omatheusmesmo.qlawkus.tools.skillhub;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Reads the SKILL.md files out of a {@code .zip} or {@code .tar.gz} archive held in memory. Nothing is
 * extracted to disk, and only SKILL.md entries are read; every other entry is checked and skipped.
 *
 * <p>The zip reader walks the central directory itself rather than streaming local headers: that is
 * where the unix mode (to refuse symlinks) and the declared sizes live, and it lets a SKILL.md be
 * inflated alone instead of decompressing the whole archive. Refusals name the entry, because the
 * model reads them to explain why the install did not happen.
 */
final class ArchiveSkillReader {

  /** A SKILL.md found in an archive: the folder that holds it ({@code ""} at the archive root) and its text. */
  record ArchivedSkill(String folder, String content) {
  }

  /**
   * The caps a read is held to: entries, total expanded bytes declared or decompressed, and the largest
   * single SKILL.md accepted.
   */
  record Limits(int maxEntries, long maxExpandedBytes, long maxSkillBytes) {
  }

  private static final String SKILL_FILE = "SKILL.md";
  private static final int ZIP_EOCD_SIG = 0x06054b50;
  private static final int ZIP_CENTRAL_SIG = 0x02014b50;
  private static final int ZIP_LOCAL_SIG = 0x04034b50;
  private static final int ZIP_EOCD_MIN = 22;
  private static final int ZIP_EOCD_SEARCH = 65_557;
  private static final int ZIP_CENTRAL_FIXED = 46;
  private static final int ZIP_LOCAL_FIXED = 30;
  private static final int ZIP_METHOD_STORED = 0;
  private static final int ZIP_METHOD_DEFLATED = 8;
  private static final int UNIX_TYPE_MASK = 0xF000;
  private static final int UNIX_SYMLINK = 0xA000;
  private static final int TAR_BLOCK = 512;
  private static final long TAR_NAME_END = 100;

  private ArchiveSkillReader() {
  }

  /**
   * Returns every SKILL.md the archive holds, in archive order.
   *
   * @throws IllegalArgumentException when the bytes are neither zip nor gzip, an entry is unsafe (path
   *     traversal, absolute or backslash name, symlink, hard link, encryption), or a cap is exceeded
   */
  static List<ArchivedSkill> read(byte[] archive, Limits limits) {
    if (archive.length >= 4 && archive[0] == 'P' && archive[1] == 'K' && archive[2] == 3 && archive[3] == 4) {
      return readZip(archive, limits);
    }
    if (archive.length >= 2 && (archive[0] & 0xff) == 0x1f && (archive[1] & 0xff) == 0x8b) {
      return readTarGz(archive, limits);
    }
    throw new IllegalArgumentException("The download is neither a .zip nor a .tar.gz archive, so no skill "
        + "could be read from it.");
  }

  private static List<ArchivedSkill> readZip(byte[] data, Limits limits) {
    int eocd = findEndOfCentralDirectory(data);
    int entryCount = u16(data, eocd + 10);
    long directorySize = u32(data, eocd + 12);
    long directoryOffset = u32(data, eocd + 16);
    if (entryCount == 0xFFFF || directoryOffset == 0xFFFFFFFFL) {
      throw new IllegalArgumentException("ZIP64 archives are not supported; repack it with fewer or smaller "
          + "entries.");
    }
    if (entryCount > limits.maxEntries()) {
      throw tooManyEntries(entryCount, limits);
    }
    if (directoryOffset + directorySize > eocd) {
      throw corrupt("the central directory runs past the end of the file");
    }
    List<ZipSkillEntry> skills = new ArrayList<>();
    long declaredExpansion = 0;
    int position = (int) directoryOffset;
    for (int i = 0; i < entryCount; i++) {
      requireBytes(data, position, ZIP_CENTRAL_FIXED, "central directory");
      if (u32(data, position) != ZIP_CENTRAL_SIG) {
        throw corrupt("a central directory entry has a bad signature");
      }
      int flags = u16(data, position + 8);
      int method = u16(data, position + 10);
      long compressedSize = u32(data, position + 20);
      long uncompressedSize = u32(data, position + 24);
      int nameLength = u16(data, position + 28);
      int extraLength = u16(data, position + 30);
      int commentLength = u16(data, position + 32);
      long externalAttributes = u32(data, position + 38);
      long localOffset = u32(data, position + 42);
      requireBytes(data, position + ZIP_CENTRAL_FIXED, nameLength, "central directory name");
      String name = new String(data, position + ZIP_CENTRAL_FIXED, nameLength, StandardCharsets.UTF_8);
      position += ZIP_CENTRAL_FIXED + nameLength + extraLength + commentLength;

      if ((flags & 1) != 0) {
        throw new IllegalArgumentException("Refused encrypted entry '" + name + "': encrypted archives are "
            + "not supported.");
      }
      if (((externalAttributes >>> 16) & UNIX_TYPE_MASK) == UNIX_SYMLINK) {
        throw new IllegalArgumentException("Refused symlink '" + name + "': archives may not contain symbolic "
            + "links.");
      }
      checkEntryName(name);
      if (name.endsWith("/")) {
        continue;
      }
      declaredExpansion += uncompressedSize;
      if (declaredExpansion > limits.maxExpandedBytes()) {
        throw expandedTooLarge(limits);
      }
      if (basename(name).equals(SKILL_FILE)) {
        skills.add(new ZipSkillEntry(name, method, compressedSize, localOffset));
      }
    }
    List<ArchivedSkill> found = new ArrayList<>();
    for (ZipSkillEntry skill : skills) {
      found.add(new ArchivedSkill(folderOf(skill.name()), readZipSkill(data, skill, limits)));
    }
    return found;
  }

  private record ZipSkillEntry(String name, int method, long compressedSize, long localOffset) {
  }

  private static String readZipSkill(byte[] data, ZipSkillEntry skill, Limits limits) {
    requireBytes(data, (int) skill.localOffset(), ZIP_LOCAL_FIXED, "local header of " + skill.name());
    if (u32(data, (int) skill.localOffset()) != ZIP_LOCAL_SIG) {
      throw corrupt("the local header of '" + skill.name() + "' has a bad signature");
    }
    int localPosition = (int) skill.localOffset();
    int nameLength = u16(data, localPosition + 26);
    int extraLength = u16(data, localPosition + 28);
    long start = (long) localPosition + ZIP_LOCAL_FIXED + nameLength + extraLength;
    if (start + skill.compressedSize() > data.length) {
      throw corrupt("the data of '" + skill.name() + "' runs past the end of the file");
    }
    InputStream raw = new ByteArrayInputStream(data, (int) start, (int) skill.compressedSize());
    byte[] bytes;
    if (skill.method() == ZIP_METHOD_STORED) {
      bytes = readBounded(raw, limits.maxSkillBytes(), skill.name());
    } else if (skill.method() == ZIP_METHOD_DEFLATED) {
      try (Inflater inflater = new Inflater(true);
          InflaterInputStream inflated = new InflaterInputStream(raw, inflater)) {
        bytes = readBounded(inflated, limits.maxSkillBytes(), skill.name());
      } catch (IOException e) {
        throw new IllegalArgumentException("Could not inflate '" + skill.name() + "': " + e.getMessage(), e);
      }
    } else {
      throw new IllegalArgumentException("Refused '" + skill.name() + "': unsupported zip compression method "
          + skill.method() + ".");
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static int findEndOfCentralDirectory(byte[] data) {
    if (data.length < ZIP_EOCD_MIN) {
      throw corrupt("the file is too short to be a zip archive");
    }
    int floor = Math.max(0, data.length - ZIP_EOCD_SEARCH);
    for (int i = data.length - ZIP_EOCD_MIN; i >= floor; i--) {
      if (u32(data, i) == ZIP_EOCD_SIG) {
        return i;
      }
    }
    throw corrupt("no zip end-of-central-directory record was found");
  }

  private static List<ArchivedSkill> readTarGz(byte[] archive, Limits limits) {
    List<ArchivedSkill> found = new ArrayList<>();
    int entries = 0;
    String pendingName = null;
    try (InputStream tar = new ExpansionLimitedStream(
        new GZIPInputStream(new ByteArrayInputStream(archive)), limits.maxExpandedBytes())) {
      byte[] header = new byte[TAR_BLOCK];
      while (true) {
        if (!readFully(tar, header)) {
          break;
        }
        if (isZeroBlock(header)) {
          break;
        }
        long size = octal(header, 124, 12);
        char type = (char) header[156];
        String name = pendingName != null ? pendingName : tarName(header);
        pendingName = null;
        if (type == 'x' || type == 'g') {
          String paxPath = paxPath(readAll(tar, size, limits.maxSkillBytes() * 4));
          skipPadding(tar, size);
          pendingName = paxPath;
          continue;
        }
        if (type == 'L') {
          pendingName = new String(readAll(tar, size, 4096), StandardCharsets.UTF_8).replace("\0", "");
          skipPadding(tar, size);
          continue;
        }
        entries++;
        if (entries > limits.maxEntries()) {
          throw tooManyEntries(entries, limits);
        }
        if (type == '2') {
          throw new IllegalArgumentException("Refused symlink '" + name + "': archives may not contain symbolic "
              + "links.");
        }
        if (type == '1') {
          throw new IllegalArgumentException("Refused hard link '" + name + "': archives may not contain hard "
              + "links.");
        }
        if (type == '5') {
          skipFully(tar, size);
          skipPadding(tar, size);
          continue;
        }
        if (type != '0' && type != '\0') {
          throw new IllegalArgumentException("Refused '" + name + "': unsupported tar entry type '" + type + "'.");
        }
        checkEntryName(name);
        if (basename(name).equals(SKILL_FILE)) {
          if (size > limits.maxSkillBytes()) {
            throw skillTooLarge(name, limits.maxSkillBytes());
          }
          byte[] content = readAll(tar, size, limits.maxSkillBytes());
          found.add(new ArchivedSkill(folderOf(name), new String(content, StandardCharsets.UTF_8)));
        } else {
          skipFully(tar, size);
        }
        skipPadding(tar, size);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("The .tar.gz archive is corrupt or truncated: " + e.getMessage(), e);
    }
    return found;
  }

  private static String tarName(byte[] header) {
    String name = cString(header, 0, (int) TAR_NAME_END);
    boolean ustar = header[257] == 'u' && header[258] == 's' && header[259] == 't' && header[260] == 'a'
        && header[261] == 'r';
    if (ustar) {
      String prefix = cString(header, 345, 155);
      if (!prefix.isEmpty()) {
        return prefix + "/" + name;
      }
    }
    return name;
  }

  private static String paxPath(byte[] record) {
    String text = new String(record, StandardCharsets.UTF_8);
    int offset = 0;
    while (offset < text.length()) {
      int space = text.indexOf(' ', offset);
      if (space < 0) {
        break;
      }
      int length = Integer.parseInt(text.substring(offset, space));
      String field = text.substring(space + 1, Math.min(text.length(), offset + length - 1));
      if (field.startsWith("path=")) {
        return field.substring("path=".length());
      }
      offset += length;
    }
    return null;
  }

  private static boolean isZeroBlock(byte[] block) {
    for (byte b : block) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private static String cString(byte[] buffer, int offset, int length) {
    int end = offset;
    while (end < offset + length && buffer[end] != 0) {
      end++;
    }
    return new String(buffer, offset, end - offset, StandardCharsets.UTF_8);
  }

  private static long octal(byte[] buffer, int offset, int length) {
    String digits = cString(buffer, offset, length).strip();
    return digits.isEmpty() ? 0 : Long.parseLong(digits, 8);
  }

  private static void skipPadding(InputStream in, long size) throws IOException {
    long padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
    skipFully(in, padding);
  }

  private static void skipFully(InputStream in, long count) throws IOException {
    long remaining = count;
    byte[] scratch = new byte[TAR_BLOCK];
    while (remaining > 0) {
      int read = in.read(scratch, 0, (int) Math.min(scratch.length, remaining));
      if (read < 0) {
        throw new IOException("unexpected end of archive");
      }
      remaining -= read;
    }
  }

  private static byte[] readAll(InputStream in, long size, long limit) throws IOException {
    if (size > limit) {
      throw new IllegalArgumentException("A tar header record of " + size + " bytes is over the limit of "
          + limit + " bytes.");
    }
    byte[] buffer = new byte[(int) size];
    int offset = 0;
    while (offset < buffer.length) {
      int read = in.read(buffer, offset, buffer.length - offset);
      if (read < 0) {
        throw new IOException("unexpected end of archive");
      }
      offset += read;
    }
    return buffer;
  }

  private static byte[] readBounded(InputStream in, long limit, String name) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      long total = 0;
      int read;
      while ((read = in.read(chunk)) >= 0) {
        total += read;
        if (total > limit) {
          throw skillTooLarge(name, limit);
        }
        out.write(chunk, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read '" + name + "' from the archive: " + e.getMessage(), e);
    }
  }

  private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
    int offset = 0;
    while (offset < buffer.length) {
      int read = in.read(buffer, offset, buffer.length - offset);
      if (read < 0) {
        if (offset == 0) {
          return false;
        }
        throw new IOException("truncated tar header");
      }
      offset += read;
    }
    return true;
  }

  private static void checkEntryName(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Refused an archive entry with no name.");
    }
    if (name.indexOf('\0') >= 0 || name.contains("\\") || name.startsWith("/")
        || name.matches("^[A-Za-z]:.*")) {
      throw new IllegalArgumentException("Refused '" + name + "': absolute and backslash paths are not allowed "
          + "in archives.");
    }
    for (String segment : name.split("/")) {
      if ("..".equals(segment)) {
        throw new IllegalArgumentException("Refused '" + name + "': path traversal ('..') is not allowed.");
      }
    }
  }

  private static String basename(String name) {
    String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    int slash = trimmed.lastIndexOf('/');
    return slash < 0 ? trimmed : trimmed.substring(slash + 1);
  }

  private static String folderOf(String name) {
    int slash = name.lastIndexOf('/');
    return slash < 0 ? "" : name.substring(0, slash);
  }

  private static IllegalArgumentException tooManyEntries(long count, Limits limits) {
    return new IllegalArgumentException("The archive holds " + count + " entries; the limit is "
        + limits.maxEntries() + " per archive.");
  }

  private static IllegalArgumentException expandedTooLarge(Limits limits) {
    return new IllegalArgumentException("The archive expands to more than " + limits.maxExpandedBytes()
        + " bytes; the limit is " + limits.maxExpandedBytes() + " bytes in total.");
  }

  private static IllegalArgumentException skillTooLarge(String name, long limit) {
    return new IllegalArgumentException("'" + name + "' is larger than the " + limit
        + "-byte limit for a SKILL.md.");
  }

  private static IllegalArgumentException corrupt(String detail) {
    return new IllegalArgumentException("The zip archive is corrupt: " + detail + ".");
  }

  private static void requireBytes(byte[] data, long offset, long length, String what) {
    if (offset < 0 || offset + length > data.length) {
      throw corrupt("the " + what + " runs past the end of the file");
    }
  }

  private static int u16(byte[] data, int offset) {
    return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
  }

  private static long u32(byte[] data, int offset) {
    return (data[offset] & 0xffL) | ((data[offset + 1] & 0xffL) << 8)
        | ((data[offset + 2] & 0xffL) << 16) | ((data[offset + 3] & 0xffL) << 24);
  }

  /** Fails the read once the decompressed bytes passed through it exceed {@code limit}. */
  private static final class ExpansionLimitedStream extends FilterInputStream {

    private final long limit;
    private long total;

    ExpansionLimitedStream(InputStream in, long limit) {
      super(in);
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value >= 0) {
        count(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = super.read(buffer, offset, length);
      if (read > 0) {
        count(read);
      }
      return read;
    }

    private void count(long bytes) {
      total += bytes;
      if (total > limit) {
        throw new IllegalArgumentException("The .tar.gz archive expands to more than " + limit
            + " bytes; the limit is " + limit + " bytes in total.");
      }
    }
  }
}
