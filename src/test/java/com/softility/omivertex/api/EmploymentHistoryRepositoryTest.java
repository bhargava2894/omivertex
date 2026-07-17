package com.softility.omivertex.api;

import com.softility.omivertex.domain.EmploymentHistory;
import com.softility.omivertex.domain.WorkMode;
import com.softility.omivertex.repository.EmploymentHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentHistoryRepositoryTest extends ApiTestBase {

    @Autowired EmploymentHistoryRepository employmentHistoryRepository;

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
        assertThat(associateRepository.findById(priya.getId()).orElseThrow().getPhone())
                .isEqualTo("+91 98765 43210");
    }
}
