package com.example.plms.core.document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side file checks: extension allow-list, magic-byte signature and a simple
 * password-protection check for PDFs. The browser-supplied MIME type is never trusted.
 */
public final class FileInspector {

    public enum Kind {
        PDF("application/pdf", "pdf"),
        JPEG("image/jpeg", "jpg", "jpeg"),
        PNG("image/png", "png");

        private final String mimeType;
        private final String[] extensions;

        Kind(String mimeType, String... extensions) {
            this.mimeType = mimeType;
            this.extensions = extensions;
        }

        public String mimeType() {
            return mimeType;
        }

        boolean acceptsExtension(String ext) {
            return Arrays.asList(extensions).contains(ext);
        }
    }

    public enum Problem { EMPTY, UNSUPPORTED_TYPE, SIGNATURE_MISMATCH, PASSWORD_PROTECTED }

    public record Inspection(Kind kind, Problem problem) {
        public boolean ok() {
            return problem == null;
        }
    }

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private FileInspector() {
    }

    public static Inspection inspect(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            return new Inspection(null, Problem.EMPTY);
        }
        Optional<Kind> byExtension = kindForExtension(extension(originalFilename));
        if (byExtension.isEmpty()) {
            return new Inspection(null, Problem.UNSUPPORTED_TYPE);
        }
        Kind kind = byExtension.get();
        byte[] magic = switch (kind) {
            case PDF -> PDF_MAGIC;
            case JPEG -> JPEG_MAGIC;
            case PNG -> PNG_MAGIC;
        };
        if (!startsWith(content, magic)) {
            return new Inspection(kind, Problem.SIGNATURE_MISMATCH);
        }
        if (kind == Kind.PDF && contains(content, "/Encrypt".getBytes(StandardCharsets.US_ASCII))) {
            return new Inspection(kind, Problem.PASSWORD_PROTECTED);
        }
        return new Inspection(kind, null);
    }

    /** Strips any path, keeps a conservative character set and bounds the length. */
    public static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        name = name.replaceAll("^\\.+", "");
        if (name.isBlank()) {
            name = "document";
        }
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }

    static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static Optional<Kind> kindForExtension(String ext) {
        for (Kind k : Kind.values()) {
            if (k.acceptsExtension(ext)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
