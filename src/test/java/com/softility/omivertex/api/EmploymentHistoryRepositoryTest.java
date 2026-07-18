package com.softility.omivertex.api;

import com.softility.omivertex.domain.EmploymentHistory;
import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// NOTE: the ON DELETE CASCADE on employment_history.associate_id exists only in
// real Postgres (V10). Tests run on a Hibernate-generated H2 schema (Flyway is
// disabled in src/test/resources), which creates no FK for the plain associateId
// column — so the cascade is untestable here, same as the resumes cascade (V3).
// Schema-parity testing is tracked in docs/TODO.md (P3).
class EmploymentHistoryRepositoryTest extends ApiTestBase {

    @Test
    void entriesRoundTrip_orderedBySortOrder_andPhonePersists() {
        var priya = associate("Priya Sharma", "priya@softility.com", WorkMode.OFFSHORE);
        priya.setPhone("+91 98765 43210");
        associateRepository.save(priya);

        var second = new EmploymentHistory();
        second.setAssociateId(priya.getId());
        second.setCompany("Initech");
        second.setTitle("Engineer");
        second.setSortOrder(1);
        employmentHistoryRepository.save(second);
        var first = new EmploymentHistory();
        first.setAssociateId(priya.getId());
        first.setCompany("Globex");
        first.setTitle("Senior Engineer");
        first.setStartDate(LocalDate.of(2021, 3, 1));
        first.setEndDate(null); // "Present" on the résumé
        first.setSortOrder(0);
        employmentHistoryRepository.save(first);

        List<EmploymentHistory> rows =
                employmentHistoryRepository.findByAssociateIdOrderBySortOrderAsc(priya.getId());
        assertThat(rows).extracting(EmploymentHistory::getCompany)
                .containsExactly("Globex", "Initech");
        assertThat(rows.get(0).getStartDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        assertThat(rows.get(0).getEndDate()).isNull(); // "Present" survives the round trip
        assertThat(associateRepository.findById(priya.getId()).orElseThrow().getPhone())
                .isEqualTo("+91 98765 43210");
    }
}
