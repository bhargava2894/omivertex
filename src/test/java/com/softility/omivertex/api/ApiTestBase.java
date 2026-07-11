package com.softility.omivertex.api;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
public abstract class ApiTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ClientRepository clientRepository;
    @Autowired protected ProjectRepository projectRepository;
    @Autowired protected AssociateRepository associateRepository;
    @Autowired protected AllocationRepository allocationRepository;
    @Autowired protected OpenPositionRepository openPositionRepository;
    @Autowired protected PositionSkillRepository positionSkillRepository;
    @Autowired protected AuditEntryRepository auditEntryRepository;
    @Autowired protected AssociateSkillRepository associateSkillRepository;
    @Autowired protected CertificationRepository certificationRepository;
    @Autowired protected SkillRepository skillRepository;
    @Autowired protected SkillCategoryRepository skillCategoryRepository;
    @Autowired protected AppUserRepository appUserRepository;
    @Autowired protected com.softility.omivertex.repository.ResumeRepository resumeRepository;
    @Autowired protected ProfileChangeRequestRepository profileChangeRequestRepository;

    @BeforeEach
    void cleanDatabase() {
        profileChangeRequestRepository.deleteAll();
        resumeRepository.deleteAll();
        appUserRepository.deleteAll();
        auditEntryRepository.deleteAll();
        associateSkillRepository.deleteAll();
        certificationRepository.deleteAll();
        allocationRepository.deleteAll();
        positionSkillRepository.deleteAll();
        openPositionRepository.deleteAll();
        projectRepository.deleteAll();
        associateRepository.deleteAll();
        clientRepository.deleteAll();
        skillRepository.deleteAll();
        skillCategoryRepository.deleteAll();
    }

    protected Skill skill(String categoryName, String skillName) {
        SkillCategory category = skillCategoryRepository.findByNameIgnoreCase(categoryName)
                .orElseGet(() -> {
                    SkillCategory c = new SkillCategory();
                    c.setName(categoryName);
                    return skillCategoryRepository.save(c);
                });
        Skill s = new Skill();
        s.setName(skillName);
        s.setCategory(category);
        return skillRepository.save(s);
    }

    protected AssociateSkill rateSkill(Associate associate, Skill skill, Proficiency proficiency) {
        AssociateSkill as = new AssociateSkill();
        as.setAssociate(associate);
        as.setSkill(skill);
        as.setProficiency(proficiency);
        return associateSkillRepository.save(as);
    }

    protected Client client(String name) {
        Client client = new Client();
        client.setName(name);
        client.setIndustry("Technology");
        client.setLocation("New York");
        return clientRepository.save(client);
    }

    protected Project project(String code, String name, Client client) {
        Project project = new Project();
        project.setCode(code);
        project.setName(name);
        project.setClient(client);
        project.setStartDate(LocalDate.now().minusMonths(6));
        return projectRepository.save(project);
    }

    protected Associate associate(String name, String email, WorkMode workMode) {
        Associate associate = new Associate();
        associate.setName(name);
        associate.setEmail(email);
        associate.setCompany("Softility");
        associate.setLocation("Hyderabad");
        associate.setWorkMode(workMode);
        associate.setDesignation("Senior Consultant");
        return associateRepository.save(associate);
    }

    protected Allocation allocation(Associate associate, Project project, boolean billable) {
        Allocation allocation = new Allocation();
        allocation.setAssociate(associate);
        allocation.setProject(project);
        allocation.setBillable(billable);
        allocation.setAllocationPercent(100);
        allocation.setStartDate(LocalDate.now().minusMonths(3));
        return allocationRepository.save(allocation);
    }
}
