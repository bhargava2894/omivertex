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

    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final AssociateRepository associates;
    private final AllocationRepository allocations;
    private final SkillCategoryRepository skillCategories;
    private final SkillRepository skills;
    private final AssociateSkillRepository associateSkills;

    public SeedDataLoader(ClientRepository clients, ProjectRepository projects,
                          AssociateRepository associates, AllocationRepository allocations,
                          SkillCategoryRepository skillCategories, SkillRepository skills,
                          AssociateSkillRepository associateSkills) {
        this.clients = clients;
        this.projects = projects;
        this.associates = associates;
        this.allocations = allocations;
        this.skillCategories = skillCategories;
        this.skills = skills;
        this.associateSkills = associateSkills;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTaxonomy();
        if (clients.count() > 0) {
            seedAssociateSkills();
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
        seedAssociateSkills();
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
}
