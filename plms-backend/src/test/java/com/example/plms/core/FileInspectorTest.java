package com.example.plms.core;

import com.example.plms.core.document.FileInspector;
import com.example.plms.core.document.FileInspector.Problem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileInspectorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    @Test
    void acceptsAllowListedTypesWithMatchingSignatures() {
        assertThat(FileInspector.inspect("a.pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)).ok()).isTrue();
        assertThat(FileInspector.inspect("a.PNG", PNG).kind()).isEqualTo(FileInspector.Kind.PNG);
        assertThat(FileInspector.inspect("a.jpeg", JPEG).kind().mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsUnsupportedMismatchedEncryptedAndEmptyFiles() {
        assertThat(FileInspector.inspect("a.exe", new byte[]{'M', 'Z'}).problem()).isEqualTo(Problem.UNSUPPORTED_TYPE);
        assertThat(FileInspector.inspect("a.png", "%PDF-1.4".getBytes()).problem()).isEqualTo(Problem.SIGNATURE_MISMATCH);
        assertThat(FileInspector.inspect("a.pdf", "%PDF-1.4 /Encrypt".getBytes()).problem())
                .isEqualTo(Problem.PASSWORD_PROTECTED);
        assertThat(FileInspector.inspect("a.pdf", new byte[0]).problem()).isEqualTo(Problem.EMPTY);
        assertThat(FileInspector.inspect(null, new byte[]{1}).problem()).isEqualTo(Problem.UNSUPPORTED_TYPE);
    }

    @Test
    void producesSafeFileNames() {
        assertThat(FileInspector.safeFileName("../../etc/pass wd.pdf")).isEqualTo("pass_wd.pdf");
        assertThat(FileInspector.safeFileName("C:\\Users\\x\\salary.pdf")).isEqualTo("salary.pdf");
        assertThat(FileInspector.safeFileName("...hidden.pdf")).isEqualTo("hidden.pdf");
        assertThat(FileInspector.safeFileName("")).isEqualTo("document");
        assertThat(FileInspector.safeFileName("<script>.pdf")).isEqualTo("_script_.pdf");
    }
}
