package com.softility.omivertex.repository;

import com.softility.omivertex.api.ApiTestBase;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ResumeRepositoryTest extends ApiTestBase {

    private Resume newResume(Long associateId, String filename, byte[] bytes) {
        Resume r = new Resume();
        r.setAssociateId(associateId);
        r.setFilename(filename);
        r.setContentType("application/pdf");
        r.setByteSize(bytes.length);
        r.setContent(bytes);
        r.setUploadedAt(Instant.now());
        return r;
    }

    @Test
    void savesAndFindsByAssociateId() {
        Associate a = associate("Meera Nair", "meera@softility.com", WorkMode.OFFSHORE);
        resumeRepository.save(newResume(a.getId(), "meera.pdf", "hello".getBytes()));

        var found = resumeRepository.findByAssociateId(a.getId());
        assertTrue(found.isPresent());
        assertEquals("meera.pdf", found.get().getFilename());
        assertArrayEquals("hello".getBytes(), found.get().getContent());
    }

    @Test
    void metaProjection_returnsMetadata() {
        Associate a = associate("Ravi K", "ravi@softility.com", WorkMode.ONSHORE);
        resumeRepository.save(newResume(a.getId(), "ravi.docx", "world".getBytes()));

        var meta = resumeRepository.findMetaByAssociateId(a.getId());
        assertTrue(meta.isPresent());
        assertEquals("ravi.docx", meta.get().getFilename());
        assertEquals(5, meta.get().getByteSize());
    }

    @Test
    void deleteByAssociateId_removesTheRow() {
        Associate a = associate("Sana P", "sana@softility.com", WorkMode.OFFSHORE);
        resumeRepository.save(newResume(a.getId(), "sana.pdf", "x".getBytes()));

        resumeRepository.deleteByAssociateId(a.getId());

        assertFalse(resumeRepository.existsByAssociateId(a.getId()));
    }
}
