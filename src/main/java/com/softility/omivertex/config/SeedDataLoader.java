package com.softility.omivertex.config;

import com.softility.omivertex.domain.*;
import com.softility.omivertex.repository.*;
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

    /** Presence of this position means the skill-gap demo scenario is already seeded. */
    private static final String SCENARIO_MARKER = "Platform Engineer";

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final SkillCategoryRepository skillCategories;
    private final SkillRepository skills;
    private final AssociateSkillRepository associateSkills;
    private final OpenPositionRepository openPositions;
    private final PositionSkillRepository positionSkills;
    private final AppUserRepository appUsers;
    private final ProfileChangeRequestRepository profileChangeRequests;

    public SeedDataLoader(ClientRepository clients, ProjectRepository projects,
                          AssociateRepository associates, AllocationRepository allocations,
                          SkillCategoryRepository skillCategories, SkillRepository skills,
                          AssociateSkillRepository associateSkills,
                          OpenPositionRepository openPositions,
                          PositionSkillRepository positionSkills,
                          AppUserRepository appUsers,
                          ProfileChangeRequestRepository profileChangeRequests) {
        this.clients = clients;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.skillCategories = skillCategories;
        this.skills = skills;
        this.associateSkills = associateSkills;
        this.openPositions = openPositions;
        this.positionSkills = positionSkills;
        this.appUsers = appUsers;
        this.profileChangeRequests = profileChangeRequests;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTaxonomy();
        if (clients.count() > 0) {
            seedAssociateSkills();
            seedSkillGapScenario();
            seedAccessRequests();
            seedProfileChanges();
            seedDemoAssociateLogin();
            return;
        }
        log.info("Seeding OmiVertex demo data");

        Client meridian = client("Meridian Health", "Healthcare", "Boston, MA");
        Client vertexBank = client("Vertex Bancorp", "Banking", "Charlotte, NC");
        Client novaRetail = client("Nova Retail Group", "Retail", "Seattle, WA");
        Client heliosEnergy = client("Helios Energy", "Energy", "Houston, TX");
        Client cascadeIns = client("Cascade Insurance", "Insurance", "Hartford, CT");
        Client orbitLogistics = client("Orbit Logistics", "Logistics", "Memphis, TN");
        Client softilityInternal = client("Softility Internal", "Internal", "HQ");

        Project mhPortal = project("MER-101", "Patient Portal Modernization", meridian, ProjectStatus.ACTIVE);
        Project mhData = project("MER-102", "Clinical Data Lake", meridian, ProjectStatus.ACTIVE);
        Project vbCore = project("VTX-201", "Core Banking Migration", vertexBank, ProjectStatus.ACTIVE);
        Project vbFraud = project("VTX-202", "Fraud Analytics Platform", vertexBank, ProjectStatus.ACTIVE);
        Project nrOmni = project("NOV-301", "Omnichannel Storefront", novaRetail, ProjectStatus.ACTIVE);
        Project nrSupply = project("NOV-302", "Supply Chain Insights", novaRetail, ProjectStatus.ON_HOLD);
        Project heGrid = project("HEL-401", "Smart Grid Telemetry", heliosEnergy, ProjectStatus.ACTIVE);
        Project ciClaims = project("CAS-501", "Claims Automation", cascadeIns, ProjectStatus.ACTIVE);
        Project olTrack = project("ORB-601", "Fleet Tracking Platform", orbitLogistics, ProjectStatus.ACTIVE);
        Project orb602 = project("ORB-602", "Warehouse Robotics Pilot", orbitLogistics, ProjectStatus.COMPLETED);
        Project nrLegacy = project("NOV-303", "Legacy System Retirement", novaRetail, ProjectStatus.COMPLETED);
        Project intBench = project("INT-001", "Bench & Training", softilityInternal, ProjectStatus.ACTIVE);

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
        Associate a25 = associate("Nina Patel", "nina.patel@softility.com", "Softility", "Mumbai, IN", WorkMode.OFFSHORE, "Consultant");
        Associate a26 = associate("John Smith", "john.smith@softility.com", "Softility", "Austin, TX", WorkMode.ONSHORE, "Business Analyst");

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
        allocate(a17, mhData, true, 100, LocalDate.now().plusDays(6));
        allocate(a20, ciClaims, true, 100, LocalDate.now().plusDays(18));

        // record an exit (spikes utilization as the denominator shrinks)
        a21.setLastWorkingDay(LocalDate.now().plusDays(40));
        associates.save(a21);
        // ended engagements — feeds bench aging
        allocate(a21, olTrack, true, 100, LocalDate.now().minusDays(45));
        allocate(a22, ciClaims, true, 100, LocalDate.now().minusDays(75));
        allocate(a23, nrLegacy, true, 100, LocalDate.now().minusDays(105));
        allocate(a24, nrLegacy, true, 100, LocalDate.now().minusDays(150));
        allocate(a25, orb602, true, 100, LocalDate.now().minusDays(90));
        allocate(a26, orb602, true, 100, LocalDate.now().minusDays(120));

        // non-billable bench/training allocation
        allocate(a23, intBench, false, 100);
        allocate(a24, intBench, false, 100);

        log.info("Seeded {} clients, {} projects, {} associates, {} allocations",
                clients.count(), projects.count(), associates.count(), allocations.count());
        seedAssociateSkills();
        seedSkillGapScenario();
        seedAccessRequests();
        seedProfileChanges();
        seedDemoAssociateLogin();
    }

    /**
     * Demo scenario for the skill-gap report. Without open positions there is no
     * demand, so every gap row would read "spare" and the report would look broken.
     * This seeds one skill in each state — Kubernetes short, React tight, Tableau
     * spare — rated and allocated so that each drill-down group (open demand, bench
     * supply, rolling off, near miss) has rows to show. Idempotent via the scenario's
     * own marker position, so it still seeds into a database that already has
     * hand-created positions, and never overwrites a rating a user entered.
     */
    private void seedSkillGapScenario() {
        if (openPositions.findAll().stream().anyMatch(p -> SCENARIO_MARKER.equals(p.getTitle()))) {
            return;
        }
        java.util.Map<String, Associate> roster = associates.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Associate::getName, a -> a, (a, b) -> a));
        java.util.Map<String, Project> byCode = projects.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Project::getCode, p -> p, (a, b) -> a));
        Skill kubernetes = skills.findFirstByNameIgnoreCase("Kubernetes").orElse(null);
        Skill react = skills.findFirstByNameIgnoreCase("React").orElse(null);
        Skill tableau = skills.findFirstByNameIgnoreCase("Tableau").orElse(null);
        if (kubernetes == null || react == null || tableau == null || roster.isEmpty() || byCode.isEmpty()) {
            return;
        }
        log.info("Seeding skill-gap demo scenario (short / tight / spare)");

        // SHORT — 3 open seats at INTERMEDIATE+, nobody on the bench holds it.
        OpenPosition platformEng = position("Platform Engineer", byCode.get("VTX-201"), 2, 30);
        require(platformEng, kubernetes, Proficiency.ADVANCE);
        OpenPosition sre = position("Site Reliability Engineer", byCode.get("HEL-401"), 1, 45);
        require(sre, kubernetes, Proficiency.INTERMEDIATE);
        rate(roster.get("Arjun Patel"), kubernetes, Proficiency.MASTERY);      // allocated, no end date
        rate(roster.get("Aditya Joshi"), kubernetes, Proficiency.ADVANCE);     // allocated, no end date
        rate(roster.get("Tom Wilson"), kubernetes, Proficiency.ADVANCE);       // rolls off in 6 days
        rate(roster.get("Sanjay Gupta"), kubernetes, Proficiency.INTERMEDIATE); // rolls off in 18 days
        rate(roster.get("Mark Davis"), kubernetes, Proficiency.FOUNDATIONAL);   // bench, one level short
        rate(roster.get("Ishaan Malhotra"), kubernetes, Proficiency.FOUNDATIONAL); // bench, one level short

        // TIGHT — 2 open seats, exactly 2 qualified people on the bench.
        OpenPosition reactDev = position("Senior React Developer", byCode.get("NOV-301"), 2, 21);
        require(reactDev, react, Proficiency.INTERMEDIATE);
        rate(roster.get("Sofia Martinez"), react, Proficiency.MASTERY);   // allocated
        rate(roster.get("Priya Sharma"), react, Proficiency.ADVANCE);     // allocated
        rate(roster.get("Alex Turner"), react, Proficiency.ADVANCE);      // bench
        rate(roster.get("Pooja Reddy"), react, Proficiency.INTERMEDIATE); // bench

        // SPARE — no open demand, 3 rated people sitting on the bench.
        rate(roster.get("David Kim"), tableau, Proficiency.MASTERY);        // allocated
        rate(roster.get("Mark Davis"), tableau, Proficiency.ADVANCE);       // bench
        rate(roster.get("Pooja Reddy"), tableau, Proficiency.INTERMEDIATE); // bench
        rate(roster.get("Ishaan Malhotra"), tableau, Proficiency.INTERMEDIATE); // bench
    }

    private OpenPosition position(String title, Project project, int headcount, int startsInDays) {
        OpenPosition position = new OpenPosition();
        position.setTitle(title);
        position.setProject(project);
        position.setHeadcount(headcount);
        position.setStartDate(LocalDate.now().plusDays(startsInDays));
        position.setStatus(PositionStatus.OPEN);
        return openPositions.save(position);
    }

    private void require(OpenPosition position, Skill skill, Proficiency minProficiency) {
        PositionSkill required = new PositionSkill();
        required.setPosition(position);
        required.setSkill(skill);
        required.setMinProficiency(minProficiency);
        required.setRequired(true);
        positionSkills.save(required);
    }

    private void rate(Associate associate, Skill skill, Proficiency proficiency) {
        if (associate == null || associateSkills.findByAssociateId(associate.getId()).stream()
                .anyMatch(existing -> existing.getSkill().getId().equals(skill.getId()))) {
            return; // never clobber a rating that is already on record
        }
        AssociateSkill rating = new AssociateSkill();
        rating.setAssociate(associate);
        rating.setSkill(skill);
        rating.setProficiency(proficiency);
        associateSkills.save(rating);
    }

    /** Seeds the full skill taxonomy once (independent of the demo-roster guard). */
    private void seedTaxonomy() {
        if (skillCategories.count() > 0) {
            return;
        }
        log.info("Seeding skill taxonomy");
        java.util.Map<String, java.util.List<String>> taxonomy = new java.util.LinkedHashMap<>();
        taxonomy.put("Programming & Scripting", java.util.List.of(
                "Java", "Python", "JavaScript", "TypeScript", "C#", "Go", "Ruby", "PHP",
                "Scala", "Kotlin", "Shell Scripting", "PowerShell"));
        taxonomy.put("Frontend", java.util.List.of(
                "React", "Angular", "Vue.js", "Next.js", "HTML/CSS", "Tailwind CSS", "Redux"));
        taxonomy.put("Backend & Frameworks", java.util.List.of(
                "Spring Boot", "Node.js", ".NET Core", "Django", "Flask", "Express.js",
                "Hibernate", "Microservices", "GraphQL", "REST API Design"));
        taxonomy.put("Cloud Platforms", java.util.List.of(
                "AWS", "Azure", "GCP", "Oracle Cloud", "IBM Cloud"));
        taxonomy.put("CI/CD", java.util.List.of(
                "Jenkins", "GitHub", "GitLab", "BitBucket", "Harness", "JFrog Artifactory",
                "Nexus", "Harbor", "Azure DevOps", "ArgoCD"));
        taxonomy.put("Containers & Orchestration", java.util.List.of(
                "Docker", "Kubernetes", "OpenShift", "Helm", "Rancher", "Amazon ECS"));
        taxonomy.put("Infrastructure as Code", java.util.List.of(
                "Terraform", "Ansible", "CloudFormation", "Puppet", "Chef", "Pulumi"));
        taxonomy.put("Observability", java.util.List.of(
                "Splunk", "Grafana", "Grafana Tempo", "Grafana Mimir", "Prometheus",
                "OpenSearch", "ELK Stack", "Datadog", "New Relic", "AppDynamics", "Alerting"));
        taxonomy.put("Databases", java.util.List.of(
                "MySQL", "PostgreSQL", "Oracle", "SQL Server", "MongoDB", "Cassandra",
                "Redis", "DynamoDB", "Snowflake", "ETL Tools"));
        taxonomy.put("Data Engineering & Analytics", java.util.List.of(
                "Apache Spark", "Hadoop", "Kafka", "Airflow", "Databricks",
                "Power BI", "Tableau", "Informatica"));
        taxonomy.put("AI / ML", java.util.List.of(
                "TensorFlow", "PyTorch", "scikit-learn", "LangChain", "OpenAI APIs",
                "MLflow", "Hugging Face"));
        taxonomy.put("ITSM", java.util.List.of(
                "ServiceNow", "Remedy", "Jira", "Jira Confluence", "PagerDuty"));
        taxonomy.put("SA Tools", java.util.List.of(
                "ScienceLogic", "Edge Dashboard", "SolarWinds", "Nagios"));
        taxonomy.put("Secret Management", java.util.List.of(
                "HashiCorp Vault", "CyberArk", "AWS Secrets Manager", "Azure Key Vault"));
        taxonomy.put("Operating Systems", java.util.List.of(
                "Linux", "Windows Server", "Unix", "macOS"));
        taxonomy.put("Security", java.util.List.of(
                "SonarQube", "Qualys", "Burp Suite", "OWASP", "Wiz", "CrowdStrike"));
        taxonomy.put("Testing & QA", java.util.List.of(
                "Selenium", "Cypress", "Playwright", "JUnit", "TestNG", "Postman", "JMeter"));
        taxonomy.put("Project & Agile", java.util.List.of(
                "Scrum", "Kanban", "SAFe", "MS Project"));

        int count = 0;
        for (var entry : taxonomy.entrySet()) {
            SkillCategory category = new SkillCategory();
            category.setName(entry.getKey());
            category = skillCategories.save(category);
            for (String skillName : entry.getValue()) {
                Skill skill = new Skill();
                skill.setName(skillName);
                skill.setCategory(category);
                skills.save(skill);
                count++;
            }
        }
        log.info("Seeded {} skill categories, {} skills", taxonomy.size(), count);
    }

    private void seedAssociateSkills() {
        Skill java = skills.findFirstByNameIgnoreCase("Java").orElse(null);
        if (java == null) {
            return;
        }
        java.util.List<Associate> list = associates.findAll();
        if (list.isEmpty()) {
            return;
        }
        long currentJavaCount = associateSkills.findAll().stream()
                .filter(as -> as.getSkill().getId().equals(java.getId()))
                .count();
        if (currentJavaCount >= 11) {
            return;
        }
        log.info("Resetting and seeding 11 Java Mastery associate ratings");
        // Delete existing Java ratings to start fresh
        associateSkills.findAll().stream()
                .filter(as -> as.getSkill().getId().equals(java.getId()))
                .forEach(associateSkills::delete);

        int count = 0;
        for (Associate a : list) {
            AssociateSkill as = new AssociateSkill();
            as.setAssociate(a);
            as.setSkill(java);
            as.setProficiency(Proficiency.MASTERY);
            associateSkills.save(as);
            count++;
            if (count >= 11) {
                break;
            }
        }
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

    /**
     * A ready-to-use ASSOCIATE login for demos: signing in with b@softility.com (a real
     * company Google account) lands on the self-service MyProfile page with its read-only
     * "My Details" block populated. Idempotent — keyed on the app-user email, so a re-seed
     * never duplicates it.
     */
    private void seedDemoAssociateLogin() {
        String email = "b@softility.com";
        if (appUsers.findByEmailIgnoreCase(email).isPresent()) return;

        Associate profile = associates.findAll().stream()
                .filter(a -> email.equalsIgnoreCase(a.getEmail()))
                .findFirst()
                .orElseGet(() -> {
                    Associate a = new Associate();
                    a.setName("Bhargava Sista");
                    a.setEmail(email);
                    a.setCompany("Softility");
                    a.setLocation("Hyderabad, IN");
                    a.setWorkMode(WorkMode.OFFSHORE);
                    a.setDesignation("Senior Consultant");
                    a.setJoinedDate(LocalDate.of(2023, 3, 12));
                    return associates.save(a);
                });

        AppUser login = new AppUser();
        login.setEmail(email);
        login.setName(profile.getName());
        login.setRole(Role.ASSOCIATE);
        login.setStatus(AccessStatus.APPROVED);
        login.setAssociateId(profile.getId());
        appUsers.save(login);
    }

    private void seedAccessRequests() {
        if (appUsers.count() > 0) return;
        AppUser req1 = new AppUser();
        req1.setEmail("new.joiner@softility.com");
        req1.setName("New Joiner");
        req1.setRole(Role.VIEWER);
        req1.setStatus(AccessStatus.PENDING);
        appUsers.save(req1);

        AppUser req2 = new AppUser();
        req2.setEmail("guest.user@softility.com");
        req2.setName("Guest User");
        req2.setRole(Role.VIEWER);
        req2.setStatus(AccessStatus.PENDING);
        appUsers.save(req2);
    }

    private void seedProfileChanges() {
        if (profileChangeRequests.count() > 0) return;

        java.util.List<Associate> all = associates.findAll();
        if (all.isEmpty()) return;

        Associate a1 = all.get(0);
        Associate a2 = all.size() > 1 ? all.get(1) : a1;

        // Payload must match SkillAssignmentRequest exactly: {"skills":[{...}]} — a bare
        // array is unreadable and used to 409 the whole admin queue. Use a real seeded
        // skill id so the pending request is actually approvable in the demo.
        Long skillId = skills.findAll().stream().findFirst().map(s -> s.getId()).orElse(1L);
        ProfileChangeRequest req1 = new ProfileChangeRequest();
        req1.setAssociate(a1);
        req1.setType(ProfileChangeType.SKILLS);
        req1.setStatus(ProfileChangeStatus.PENDING);
        req1.setSkillsPayload(
                "{\"skills\":[{\"skillId\":" + skillId + ",\"proficiency\":\"ADVANCE\",\"primary\":true}]}");
        profileChangeRequests.save(req1);

        ProfileChangeRequest req2 = new ProfileChangeRequest();
        req2.setAssociate(a2);
        req2.setType(ProfileChangeType.RESUME);
        req2.setStatus(ProfileChangeStatus.PENDING);
        req2.setResumeFilename("updated_resume.pdf");
        req2.setResumeContentType("application/pdf");
        req2.setResumeByteSize(1024L);
        req2.setResumeContent(new byte[]{1,2,3,4});
        profileChangeRequests.save(req2);
    }
}
