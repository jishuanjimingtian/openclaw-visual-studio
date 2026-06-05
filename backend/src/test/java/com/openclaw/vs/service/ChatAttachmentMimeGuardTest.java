package com.openclaw.vs.service;

import com.openclaw.vs.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAttachmentMimeGuardTest {

    @Test
    void allowsImagesAndDocuments() {
        assertThatCode(() -> ChatAttachmentMimeGuard.validateInit("a.png", "image/png", 1024))
            .doesNotThrowAnyException();
        assertThatCode(() -> ChatAttachmentMimeGuard.validateInit("doc.pdf", "application/pdf", 1024))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsExecutableAndOversize() {
        assertThatThrownBy(() -> ChatAttachmentMimeGuard.validateInit("run.exe", "application/octet-stream", 1024))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ChatAttachmentMimeGuard.validateInit("big.pdf", "application/pdf", 101L * 1024 * 1024))
            .isInstanceOf(BadRequestException.class);
    }
}
