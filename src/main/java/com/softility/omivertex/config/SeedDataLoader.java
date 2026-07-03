package com.softility.omivertex.config;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "omivertex.seed", havingValue = "true")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;

    public SeedDataLoader(ClientRepository clients, ProjectRepository projects,
                          AssociateRepository associates, AllocationRepository allocations) {
        this.clients = clients;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clients.count() > 0) {
            return;
        }
        log.info("Seeding OmiVertex demo data");

        Client meridian = client("Meridian Health", "Healthcare", "Boston, MA");
        Client vertexBank = client("Vertex Bancorp", "Banking", "Charlotte, NC");
        Client novaRetail = client("Nova Retail Group", "Retail", "Seattle, WA");
        Client heliosEnergy = client("Helios Energy", "Energy", "Houston, TX");
        Client cascadeIns = client("Cascade Insurance", "Insurance", "Hartford, CT");
        Client orbitLogistics = client("Orbit Logistics", "Logistics", "Memphis, TN");

        Project mhPortal = project("MER-101", "Patient Portal Modernization", meridian, ProjectStatus.ACTIVE);
        Project mhData = project("MER-102", "Clinical Data Lake", meridian, ProjectStatus.ACTIVE);
        Project vbCore = project("VTX-201", "Core Banking Migration", vertexBank, ProjectStatus.ACTIVE);
        Project vbFraud = project("VTX-202", "Fraud Analytics Platform", vertexBank, ProjectStatus.ACTIVE);
        Project nrOmni = project("NOV-301", "Omnichannel Storefront", novaRetail, ProjectStatus.ACTIVE);
        Project nrSupply = project("NOV-302", "Supply Chain Insights", novaRetail, ProjectStatus.ON_HOLD);
        Project heGrid = project("HEL-401", "Smart Grid Telemetry", heliosEnergy, ProjectStatus.ACTIVE);
        Project ciClaims = project("CAS-501", "Claims Automation", cascadeIns, ProjectStatus.ACTIVE);
        Project olTrack = project("ORB-601", "Fleet Tracking Platform", orbitLogistics, ProjectStatus.ACTIVE);
        project("ORB-602", "Warehouse Robotics Pilot", orbitLogistics, ProjectStatus.COMPLETED);

        Associate a1 = associate("Priya Sharma", "priya.sharma@softility.com", "Softility", "Hyderabad, IN", WorkMode.OFFSHORE, "Senior Consultant");
        Associate a2 = associate("Rahul Verma", "rahul.verma@softility.com", "Softility", "Dallas, TX", WorkMode.ONSHORE, "Lead Consultant");
        Associate a3 = associate("Anita Rao", "anita.rao@softility.com", "Softility", "Bengaluru, IN", WorkMode.OFFSHORE, "Consultant");
        Associate a4 = associate("James Carter", "james.carter@softility.com", "Softility", "Boston, MA", WorkMode.ONSHORE, "Solution Architect");
        Associate a5 = associate("Meera Iyer", "meera.iyer@softility.com", "Softility", "Chennai, IN", WorkMode.OFFSHORE, "Senior Consultant");
        Associate a6 = associate("David Kim", "david.kim@softility.com", "Softility", "Charlotte, NC", WorkMode.ONSHORE, "Data Engineer");
        Associate a7 = associate("Sofia Martinez", "sofia.martinez@softility.com", "Softility", "Austin, TX", WorkMode.ONSHORE, "UX Engineer");
        Associate a8 = associate("Arjun Patel", "arjun.patel@softility.com", "Softility", "Pune, IN", WorkMode.OFFSHORE, "DevOps Engineer");
        Associate a9 = associate("Emily Chen", "emily.chen@softility.com", "Softility", "Seattle, WA", WorkMode.ONSHORE, "Senior Consultant");
        Associate a10 = associate("Vikram Singh", "vikram.singh@softility.com", "Softility", "Hyderabad, IN", WorkMode.OFFSHORE, "QA Lead");
        Associate a11 = associate("Laura Johnson", "laura.johnson@softility.com", "Softility", "Houston, TX", WorkMode.ONSHORE, "Program Manager");
        Associate a12 = associate("Karthik Nair", "karthik.nair@softility.com", "Softility", "Kochi, IN", WorkMode.OFFSHORE, "Consultant");
        Associate a13 = associate("Rachel Adams", "rachel.adams@softility.com", "Softility", "Hartford, CT", WorkMode.ONSHORE, "Business Analyst");
        Associate a14 = associate("Suresh Kumar", "suresh.kumar@softility.com", "Softility", "Chennai, IN", WorkMode.OFFSHORE, "Data Engineer");
        Associate a15 = associate("Nina Petrova", "nina.petrova@softility.com", "Softility", "Memphis, TN", WorkMode.ONSHORE, "Senior Consultant");
        Associate a16 = associate("Aditya Joshi", "aditya.joshi@softility.com", "Softility", "Mumbai, IN", WorkMode.OFFSHORE, "Cloud Engineer");
        Associate a17 = associate("Tom Wilson", "tom.wilson@softility.com", "Softility", "Boston, MA", WorkMode.ONSHORE, "Consultant");
        Associate a18 = associate("Divya Menon", "divya.menon@softility.com", "Softility", "Bengaluru, IN", WorkMode.OFFSHORE, "ML Engineer");
        Associate a19 = associate("Chris Evans", "chris.evans@softility.com", "Softility", "Dallas, TX", WorkMode.ONSHORE, "Security Engineer");
        Associate a20 = associate("Sanjay Gupta", "sanjay.gupta@softility.com", "Softility", "Noida, IN", WorkMode.OFFSHORE, "Consultant");
        // Bench
        Associate a21 = associate("Alex Turner", "alex.turner@softility.com", "Softility", "Chicago, IL", WorkMode.ONSHORE, "Consultant");
        Associate a22 = associate("Pooja Reddy", "pooja.reddy@softility.com", "Softility", "Hyderabad, IN", WorkMode.OFFSHORE, "Junior Consultant");
        Associate a23 = associate("Mark Davis", "mark.davis@softility.com", "Softility", "Atlanta, GA", WorkMode.ONSHORE, "Data Analyst");
        Associate a24 = associate("Ishaan Malhotra", "ishaan.malhotra@softility.com", "Softility", "Gurgaon, IN", WorkMode.OFFSHORE, "Consultant");

        allocate(a1, mhPortal, true, 100);
        allocate(a2, mhPortal, true, 100);
        allocate(a3, mhData, true, 100);
        allocate(a4, mhPortal, true, 50);
        allocate(a4, mhData, true, 50);
        allocate(a5, vbCore, true, 100);
        allocate(a6, vbFraud, true, 100);
        allocate(a7, nrOmni, true, 100);
        allocate(a8, vbCore, false, 100);
        allocate(a9, nrOmni, true, 100);
        allocate(a10, ciClaims, true, 100);
        allocate(a11, heGrid, true, 100);
        allocate(a12, nrSupply, false, 100);
        allocate(a13, ciClaims, true, 100);
        allocate(a14, heGrid, true, 100);
        allocate(a15, olTrack, true, 100);
        allocate(a16, olTrack, true, 100);
        allocate(a18, vbFraud, true, 100);
        allocate(a19, vbCore, true, 100);
        // rolling off soon — feeds the roll-off radar
        allocate(a17, mhData, false, 100, LocalDate.now().plusDays(6));
        allocate(a20, ciClaims, false, 100, LocalDate.now().plusDays(18));
        // ended engagements — feeds bench aging
        allocate(a21, olTrack, true, 100, LocalDate.now().minusDays(45));
        allocate(a22, ciClaims, true, 100, LocalDate.now().minusDays(75));

        log.info("Seeded {} clients, {} projects, {} associates, {} allocations",
                clients.count(), projects.count(), associates.count(), allocations.count());
    }

    private Client client(String name, String industry, String location) {
        Client client = new Client();
        client.setName(name);
        client.setIndustry(industry);
        client.setLocation(location);
        return clients.save(client);
    }

    private Project project(String code, String name, Client client, ProjectStatus status) {
        Project project = new Project();
        project.setCode(code);
        project.setName(name);
        project.setClient(client);
        project.setStatus(status);
        project.setStartDate(LocalDate.now().minusMonths(8));
        if (status == ProjectStatus.COMPLETED) {
            project.setEndDate(LocalDate.now().minusMonths(1));
        }
        return projects.save(project);
    }

    private Associate associate(String name, String email, String company, String location,
                                WorkMode workMode, String designation) {
        Associate associate = new Associate();
        associate.setName(name);
        associate.setEmail(email);
        associate.setCompany(company);
        associate.setLocation(location);
        associate.setWorkMode(workMode);
        associate.setDesignation(designation);
        return associates.save(associate);
    }

    private void allocate(Associate associate, Project project, boolean billable, int percent) {
        allocate(associate, project, billable, percent, null);
    }

    private void allocate(Associate associate, Project project, boolean billable, int percent, LocalDate endDate) {
        Allocation allocation = new Allocation();
        allocation.setAssociate(associate);
        allocation.setProject(project);
        allocation.setBillable(billable);
        allocation.setAllocationPercent(percent);
        allocation.setStartDate(LocalDate.now().minusMonths(7));
        allocation.setEndDate(endDate);
        allocations.save(allocation);
    }
}
