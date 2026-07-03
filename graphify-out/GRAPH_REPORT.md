# Graph Report - .  (2026-07-03)

## Corpus Check
- 100 files · ~56,363 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 560 nodes · 583 edges · 94 communities detected
- Extraction: 97% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.81)
- Token cost: 93,197 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Product Concepts & Design Rationale|Product Concepts & Design Rationale]]
- [[_COMMUNITY_Associate Entity|Associate Entity]]
- [[_COMMUNITY_OpenPosition Entity|OpenPosition Entity]]
- [[_COMMUNITY_Allocation Entity|Allocation Entity]]
- [[_COMMUNITY_Project Entity|Project Entity]]
- [[_COMMUNITY_Roster Import Service|Roster Import Service]]
- [[_COMMUNITY_Associate API Tests|Associate API Tests]]
- [[_COMMUNITY_Allocation API Tests|Allocation API Tests]]
- [[_COMMUNITY_ImportExport Tests|Import/Export Tests]]
- [[_COMMUNITY_Position Service|Position Service]]
- [[_COMMUNITY_Client Entity|Client Entity]]
- [[_COMMUNITY_App User & Access Requests|App User & Access Requests]]
- [[_COMMUNITY_Position API Tests|Position API Tests]]
- [[_COMMUNITY_SVG Chart Components|SVG Chart Components]]
- [[_COMMUNITY_Client API Tests|Client API Tests]]
- [[_COMMUNITY_Auth API Tests|Auth API Tests]]
- [[_COMMUNITY_Allocation Service|Allocation Service]]
- [[_COMMUNITY_Project API Tests|Project API Tests]]
- [[_COMMUNITY_Position Controller|Position Controller]]
- [[_COMMUNITY_Client Service|Client Service]]
- [[_COMMUNITY_Associate Service|Associate Service]]
- [[_COMMUNITY_Project Service|Project Service]]
- [[_COMMUNITY_ExportService|ExportService]]
- [[_COMMUNITY_GlobalExceptionHandler|GlobalExceptionHandler]]
- [[_COMMUNITY_AllocationRepository|AllocationRepository]]
- [[_COMMUNITY_SeedDataLoader|SeedDataLoader]]
- [[_COMMUNITY_ClientController|ClientController]]
- [[_COMMUNITY_AssociateController|AssociateController]]
- [[_COMMUNITY_AuthController|AuthController]]
- [[_COMMUNITY_ProjectController|ProjectController]]
- [[_COMMUNITY_AllocationController|AllocationController]]
- [[_COMMUNITY_ApiTestBase|ApiTestBase]]
- [[_COMMUNITY_ProjectRepository|ProjectRepository]]
- [[_COMMUNITY_OmiVertex Logo Mark (frontend source)|OmiVertex Logo Mark (frontend source)]]
- [[_COMMUNITY_AdminUserController|AdminUserController]]
- [[_COMMUNITY_OmiVertex Brand Logo Lockup (frontend source)|OmiVertex Brand Logo Lockup (frontend source)]]
- [[_COMMUNITY_OpenPositionRepository|OpenPositionRepository]]
- [[_COMMUNITY_SecurityConfig|SecurityConfig]]
- [[_COMMUNITY_DataTransferController|DataTransferController]]
- [[_COMMUNITY_DashboardService|DashboardService]]
- [[_COMMUNITY_README|README]]
- [[_COMMUNITY_theme.js|theme.js]]
- [[_COMMUNITY_Dashboard.jsx|Dashboard.jsx]]
- [[_COMMUNITY_DashboardApiTest|DashboardApiTest]]
- [[_COMMUNITY_AppUserRepository|AppUserRepository]]
- [[_COMMUNITY_ClientRepository|ClientRepository]]
- [[_COMMUNITY_AssociateRepository|AssociateRepository]]
- [[_COMMUNITY_DashboardController|DashboardController]]
- [[_COMMUNITY_App()|App()]]
- [[_COMMUNITY_DataTransfer.jsx|DataTransfer.jsx]]
- [[_COMMUNITY_Allocations.jsx|Allocations.jsx]]
- [[_COMMUNITY_Login.jsx|Login.jsx]]
- [[_COMMUNITY_Associates.jsx|Associates.jsx]]
- [[_COMMUNITY_Settings.jsx|Settings.jsx]]
- [[_COMMUNITY_OmivertexApplicationTests|OmivertexApplicationTests]]
- [[_COMMUNITY_OmivertexApplication|OmivertexApplication]]
- [[_COMMUNITY_HomeController|HomeController]]
- [[_COMMUNITY_benchDays()|benchDays()]]
- [[_COMMUNITY_UnauthorizedException|UnauthorizedException]]
- [[_COMMUNITY_ConflictException|ConflictException]]
- [[_COMMUNITY_BadRequestException|BadRequestException]]
- [[_COMMUNITY_NotFoundException|NotFoundException]]
- [[_COMMUNITY_Access Requests Approval Workflow|Access Requests Approval Workflow]]
- [[_COMMUNITY_hooks.js|hooks.js]]
- [[_COMMUNITY_request()|request()]]
- [[_COMMUNITY_DataTable()|DataTable()]]
- [[_COMMUNITY_Modal.jsx|Modal.jsx]]
- [[_COMMUNITY_Badge()|Badge()]]
- [[_COMMUNITY_Icon.jsx|Icon.jsx]]
- [[_COMMUNITY_Field()|Field()]]
- [[_COMMUNITY_Clients()|Clients()]]
- [[_COMMUNITY_Projects.jsx|Projects.jsx]]
- [[_COMMUNITY_AccessRequests()|AccessRequests()]]
- [[_COMMUNITY_Positions.jsx|Positions.jsx]]
- [[_COMMUNITY_from()|from()]]
- [[_COMMUNITY_from()|from()]]
- [[_COMMUNITY_from()|from()]]
- [[_COMMUNITY_from()|from()]]
- [[_COMMUNITY_vite.config.js|vite.config.js]]
- [[_COMMUNITY_main.jsx|main.jsx]]
- [[_COMMUNITY_MatchCandidateResponse.java|MatchCandidateResponse.java]]
- [[_COMMUNITY_ClientRequest.java|ClientRequest.java]]
- [[_COMMUNITY_AllocationRequest.java|AllocationRequest.java]]
- [[_COMMUNITY_AssociateRequest.java|AssociateRequest.java]]
- [[_COMMUNITY_DashboardSummaryResponse.java|DashboardSummaryResponse.java]]
- [[_COMMUNITY_FillPositionRequest.java|FillPositionRequest.java]]
- [[_COMMUNITY_AllocationUpdateRequest.java|AllocationUpdateRequest.java]]
- [[_COMMUNITY_PositionRequest.java|PositionRequest.java]]
- [[_COMMUNITY_ProjectRequest.java|ProjectRequest.java]]
- [[_COMMUNITY_ImportSummaryResponse.java|ImportSummaryResponse.java]]
- [[_COMMUNITY_ProjectStatus.java|ProjectStatus.java]]
- [[_COMMUNITY_PositionStatus.java|PositionStatus.java]]
- [[_COMMUNITY_EntityStatus.java|EntityStatus.java]]
- [[_COMMUNITY_WorkMode.java|WorkMode.java]]

## God Nodes (most connected - your core abstractions)
1. `Associate` - 22 edges
2. `OpenPosition` - 18 edges
3. `Allocation` - 17 edges
4. `Project` - 16 edges
5. `ImportService` - 14 edges
6. `AssociateApiTest` - 12 edges
7. `AllocationApiTest` - 12 edges
8. `DataTransferApiTest` - 12 edges
9. `PositionService` - 12 edges
10. `Client` - 12 edges

## Surprising Connections (you probably didn't know these)
- `CLAUDE.md Graphify Instructions` --conceptually_related_to--> `OmiVertex System`  [AMBIGUOUS]
  CLAUDE.md → README.md
- `REST API (/api/v1)` --implements--> `OmiVertex System`  [INFERRED]
  docs/TECHNICAL.md → README.md
- `React SPA Frontend` --implements--> `OmiVertex System`  [INFERRED]
  docs/TECHNICAL.md → README.md
- `README` --references--> `Technical Documentation`  [EXTRACTED]
  README.md → docs/TECHNICAL.md
- `README` --references--> `Functional Overview`  [EXTRACTED]
  README.md → docs/FUNCTIONAL_OVERVIEW.md

## Hyperedges (group relationships)
- **Core Domain Data Model (Client 1-* Project 1-* Allocation *-1 Associate)** — technical_client, technical_project, technical_allocation, technical_associate [EXTRACTED 1.00]
- **Bench Visibility and Utilization Flow on the Dashboard** — technical_bench, functional_bench_aging, functional_rolloff_radar, functional_utilization, technical_dashboard_summary [EXTRACTED 1.00]
- **Spreadsheet Import Pipeline (find-or-create clients/projects/associates, allocate)** — technical_import_service, technical_client, technical_project, technical_associate, technical_allocation [EXTRACTED 1.00]

## Communities

### Community 0 - "Product Concepts & Design Rationale"
Cohesion: 0.09
Nodes (32): CLAUDE.md Graphify Instructions, Rationale: Normalize Customer/Project/Billable into Allocation, Rationale: Single Spring Boot Jar over Separate SPA Deployment, Bench Aging Buckets, Bench-to-Demand Matching (Roadmap), Roll-off Radar, Six-Month Staffing Trend, Billable Utilization (FTE-weighted) (+24 more)

### Community 1 - "Associate Entity"
Cohesion: 0.09
Nodes (1): Associate

### Community 2 - "OpenPosition Entity"
Cohesion: 0.11
Nodes (1): OpenPosition

### Community 3 - "Allocation Entity"
Cohesion: 0.11
Nodes (1): Allocation

### Community 4 - "Project Entity"
Cohesion: 0.12
Nodes (1): Project

### Community 5 - "Roster Import Service"
Cohesion: 0.24
Nodes (1): ImportService

### Community 6 - "Associate API Tests"
Cohesion: 0.15
Nodes (1): AssociateApiTest

### Community 7 - "Allocation API Tests"
Cohesion: 0.15
Nodes (1): AllocationApiTest

### Community 8 - "Import/Export Tests"
Cohesion: 0.23
Nodes (1): DataTransferApiTest

### Community 9 - "Position Service"
Cohesion: 0.27
Nodes (1): PositionService

### Community 10 - "Client Entity"
Cohesion: 0.15
Nodes (1): Client

### Community 11 - "App User & Access Requests"
Cohesion: 0.15
Nodes (1): AppUser

### Community 12 - "Position API Tests"
Cohesion: 0.29
Nodes (1): PositionApiTest

### Community 13 - "SVG Chart Components"
Cohesion: 0.35
Nodes (8): DonutChart(), getDonutSlicePath(), HBarChart(), niceTicks(), StackedBar(), TrendChart(), useWidth(), VBarChart()

### Community 14 - "Client API Tests"
Cohesion: 0.18
Nodes (1): ClientApiTest

### Community 15 - "Auth API Tests"
Cohesion: 0.25
Nodes (1): AuthApiTest

### Community 16 - "Allocation Service"
Cohesion: 0.29
Nodes (1): AllocationService

### Community 17 - "Project API Tests"
Cohesion: 0.2
Nodes (1): ProjectApiTest

### Community 18 - "Position Controller"
Cohesion: 0.2
Nodes (1): PositionController

### Community 19 - "Client Service"
Cohesion: 0.31
Nodes (1): ClientService

### Community 20 - "Associate Service"
Cohesion: 0.33
Nodes (1): AssociateService

### Community 21 - "Project Service"
Cohesion: 0.31
Nodes (1): ProjectService

### Community 22 - "ExportService"
Cohesion: 0.4
Nodes (1): ExportService

### Community 23 - "GlobalExceptionHandler"
Cohesion: 0.36
Nodes (2): GlobalExceptionHandler, of()

### Community 24 - "AllocationRepository"
Cohesion: 0.25
Nodes (1): AllocationRepository

### Community 25 - "SeedDataLoader"
Cohesion: 0.39
Nodes (1): SeedDataLoader

### Community 26 - "ClientController"
Cohesion: 0.25
Nodes (1): ClientController

### Community 27 - "AssociateController"
Cohesion: 0.25
Nodes (1): AssociateController

### Community 28 - "AuthController"
Cohesion: 0.29
Nodes (2): AuthController, from()

### Community 29 - "ProjectController"
Cohesion: 0.25
Nodes (1): ProjectController

### Community 30 - "AllocationController"
Cohesion: 0.25
Nodes (1): AllocationController

### Community 31 - "ApiTestBase"
Cohesion: 0.29
Nodes (1): ApiTestBase

### Community 32 - "ProjectRepository"
Cohesion: 0.29
Nodes (1): ProjectRepository

### Community 33 - "OmiVertex Logo Mark (frontend source)"
Cohesion: 0.43
Nodes (7): OmiVertex Logo Mark (frontend source), Hexagonal Geodesic Wireframe, Network Graph / Connected Vertices Concept, OmiVertex Brand Identity, Pink-to-Blue Diagonal Gradient, Interconnected People and Resources, Vertex Dots at Line Intersections

### Community 34 - "AdminUserController"
Cohesion: 0.33
Nodes (1): AdminUserController

### Community 35 - "OmiVertex Brand Logo Lockup (frontend source)"
Cohesion: 0.47
Nodes (6): Pink-to-Teal Gradient Color Scheme, Graph / Vertex Network Concept, OmiVertex Brand Logo Lockup (frontend source), OmiVertex Resource-Management App, OmiVertex Wordmark, Polyhedral Network Mark

### Community 36 - "OpenPositionRepository"
Cohesion: 0.4
Nodes (1): OpenPositionRepository

### Community 37 - "SecurityConfig"
Cohesion: 0.4
Nodes (1): SecurityConfig

### Community 38 - "DataTransferController"
Cohesion: 0.4
Nodes (1): DataTransferController

### Community 39 - "DashboardService"
Cohesion: 0.5
Nodes (1): DashboardService

### Community 40 - "README"
Cohesion: 0.6
Nodes (5): OmiVertex Design Document, Functional Overview, OmiVertex Implementation Plan, README, Technical Documentation

### Community 41 - "theme.js"
Cohesion: 0.67
Nodes (2): applyTheme(), resolveTheme()

### Community 42 - "Dashboard.jsx"
Cohesion: 0.5
Nodes (0): 

### Community 43 - "DashboardApiTest"
Cohesion: 0.5
Nodes (1): DashboardApiTest

### Community 44 - "AppUserRepository"
Cohesion: 0.5
Nodes (1): AppUserRepository

### Community 45 - "ClientRepository"
Cohesion: 0.5
Nodes (1): ClientRepository

### Community 46 - "AssociateRepository"
Cohesion: 0.5
Nodes (1): AssociateRepository

### Community 47 - "DashboardController"
Cohesion: 0.5
Nodes (1): DashboardController

### Community 48 - "App()"
Cohesion: 1.0
Nodes (2): App(), useHashRoute()

### Community 49 - "DataTransfer.jsx"
Cohesion: 0.67
Nodes (0): 

### Community 50 - "Allocations.jsx"
Cohesion: 0.67
Nodes (0): 

### Community 51 - "Login.jsx"
Cohesion: 0.67
Nodes (0): 

### Community 52 - "Associates.jsx"
Cohesion: 0.67
Nodes (0): 

### Community 53 - "Settings.jsx"
Cohesion: 0.67
Nodes (0): 

### Community 54 - "OmivertexApplicationTests"
Cohesion: 0.67
Nodes (1): OmivertexApplicationTests

### Community 55 - "OmivertexApplication"
Cohesion: 0.67
Nodes (1): OmivertexApplication

### Community 56 - "HomeController"
Cohesion: 0.67
Nodes (1): HomeController

### Community 57 - "benchDays()"
Cohesion: 1.0
Nodes (2): benchDays(), from()

### Community 58 - "UnauthorizedException"
Cohesion: 0.67
Nodes (1): UnauthorizedException

### Community 59 - "ConflictException"
Cohesion: 0.67
Nodes (1): ConflictException

### Community 60 - "BadRequestException"
Cohesion: 0.67
Nodes (1): BadRequestException

### Community 61 - "NotFoundException"
Cohesion: 0.67
Nodes (1): NotFoundException

### Community 62 - "Access Requests Approval Workflow"
Cohesion: 1.0
Nodes (3): Access Requests Approval Workflow, AppUser Entity, Company-Email Sign-in Flow

### Community 63 - "hooks.js"
Cohesion: 1.0
Nodes (0): 

### Community 64 - "request()"
Cohesion: 1.0
Nodes (0): 

### Community 65 - "DataTable()"
Cohesion: 1.0
Nodes (0): 

### Community 66 - "Modal.jsx"
Cohesion: 1.0
Nodes (0): 

### Community 67 - "Badge()"
Cohesion: 1.0
Nodes (0): 

### Community 68 - "Icon.jsx"
Cohesion: 1.0
Nodes (0): 

### Community 69 - "Field()"
Cohesion: 1.0
Nodes (0): 

### Community 70 - "Clients()"
Cohesion: 1.0
Nodes (0): 

### Community 71 - "Projects.jsx"
Cohesion: 1.0
Nodes (0): 

### Community 72 - "AccessRequests()"
Cohesion: 1.0
Nodes (0): 

### Community 73 - "Positions.jsx"
Cohesion: 1.0
Nodes (0): 

### Community 74 - "from()"
Cohesion: 1.0
Nodes (0): 

### Community 75 - "from()"
Cohesion: 1.0
Nodes (0): 

### Community 76 - "from()"
Cohesion: 1.0
Nodes (0): 

### Community 77 - "from()"
Cohesion: 1.0
Nodes (0): 

### Community 78 - "vite.config.js"
Cohesion: 1.0
Nodes (0): 

### Community 79 - "main.jsx"
Cohesion: 1.0
Nodes (0): 

### Community 80 - "MatchCandidateResponse.java"
Cohesion: 1.0
Nodes (0): 

### Community 81 - "ClientRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 82 - "AllocationRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 83 - "AssociateRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 84 - "DashboardSummaryResponse.java"
Cohesion: 1.0
Nodes (0): 

### Community 85 - "FillPositionRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 86 - "AllocationUpdateRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 87 - "PositionRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 88 - "ProjectRequest.java"
Cohesion: 1.0
Nodes (0): 

### Community 89 - "ImportSummaryResponse.java"
Cohesion: 1.0
Nodes (0): 

### Community 90 - "ProjectStatus.java"
Cohesion: 1.0
Nodes (0): 

### Community 91 - "PositionStatus.java"
Cohesion: 1.0
Nodes (0): 

### Community 92 - "EntityStatus.java"
Cohesion: 1.0
Nodes (0): 

### Community 93 - "WorkMode.java"
Cohesion: 1.0
Nodes (0): 

## Ambiguous Edges - Review These
- `CLAUDE.md Graphify Instructions` → `OmiVertex System`  [AMBIGUOUS]
  CLAUDE.md · relation: conceptually_related_to

## Knowledge Gaps
- **7 isolated node(s):** `CLAUDE.md Graphify Instructions`, `No Duplicate Open Allocation Rule`, `ExportService (xlsx/csv/pdf/docx)`, `Hash Router (useHashRoute)`, `SeedDataLoader` (+2 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `hooks.js`** (2 nodes): `hooks.js`, `useLoad()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `request()`** (2 nodes): `request()`, `api.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `DataTable()`** (2 nodes): `DataTable()`, `DataTable.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Modal.jsx`** (2 nodes): `Modal.jsx`, `Modal()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Badge()`** (2 nodes): `Badge()`, `Badge.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Icon.jsx`** (2 nodes): `Icon.jsx`, `Icon()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Field()`** (2 nodes): `Field()`, `Field.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Clients()`** (2 nodes): `Clients()`, `Clients.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Projects.jsx`** (2 nodes): `Projects.jsx`, `Projects()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `AccessRequests()`** (2 nodes): `AccessRequests()`, `AccessRequests.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Positions.jsx`** (2 nodes): `Positions.jsx`, `Positions()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `from()`** (2 nodes): `from()`, `ProjectResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `from()`** (2 nodes): `from()`, `AllocationResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `from()`** (2 nodes): `from()`, `PositionResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `from()`** (2 nodes): `from()`, `ClientResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `vite.config.js`** (1 nodes): `vite.config.js`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `main.jsx`** (1 nodes): `main.jsx`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `MatchCandidateResponse.java`** (1 nodes): `MatchCandidateResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ClientRequest.java`** (1 nodes): `ClientRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `AllocationRequest.java`** (1 nodes): `AllocationRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `AssociateRequest.java`** (1 nodes): `AssociateRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `DashboardSummaryResponse.java`** (1 nodes): `DashboardSummaryResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `FillPositionRequest.java`** (1 nodes): `FillPositionRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `AllocationUpdateRequest.java`** (1 nodes): `AllocationUpdateRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `PositionRequest.java`** (1 nodes): `PositionRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ProjectRequest.java`** (1 nodes): `ProjectRequest.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ImportSummaryResponse.java`** (1 nodes): `ImportSummaryResponse.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `ProjectStatus.java`** (1 nodes): `ProjectStatus.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `PositionStatus.java`** (1 nodes): `PositionStatus.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `EntityStatus.java`** (1 nodes): `EntityStatus.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `WorkMode.java`** (1 nodes): `WorkMode.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `CLAUDE.md Graphify Instructions` and `OmiVertex System`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What connects `CLAUDE.md Graphify Instructions`, `No Duplicate Open Allocation Rule`, `ExportService (xlsx/csv/pdf/docx)` to the rest of the system?**
  _7 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Product Concepts & Design Rationale` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Associate Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `OpenPosition Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Allocation Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Project Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._