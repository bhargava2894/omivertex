# Capacity Integrity & Staffing Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the import capacity hole (no more 200% allocations), add End/Assign actions to the Associate Profile, and add per-company billable/non-billable staffing views (dashboard split + drill-down Staffing page).

**Architecture:** The existing `AllocationService.assertCapacity` guard becomes callable from `ImportService` (one implementation of the rule, no copies). Profile actions reuse the existing guarded allocation endpoints via a shared `AllocationForm` component extracted from `Allocations.jsx`. A new read-only `GET /api/v1/staffing` returns a client → project → associates tree; the dashboard's `ClientHeadcount` gains `clientId`/`billable`/`nonBillable`.

**Tech Stack:** Spring Boot 3.5 / Java 21, Spring Data JPA (H2 API tests via `ApiTestBase`), React 18 + Vite. Backend strict TDD; frontend verified by `npm run build` + manual smoke (no JS test runner in this repo — stated exception, consistent with prior features).

**Spec:** `docs/superpowers/specs/2026-07-10-capacity-staffing-design.md`
**Branch:** `feature/capacity-staffing` (already created)

---

## File Structure

**Backend — modify:**
- `src/main/java/com/softility/omivertex/service/AllocationService.java` — widen `assertCapacity` to package-private.
- `src/main/java/com/softility/omivertex/service/ImportService.java` — inject `AllocationService`, check capacity before creating import allocations.
- `src/main/java/com/softility/omivertex/service/DashboardService.java` — per-client billable/non-billable split.
- `src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java` — extend `ClientHeadcount`.

**Backend — create:**
- `src/main/java/com/softility/omivertex/web/dto/StaffingDtos.java` — response records.
- `src/main/java/com/softility/omivertex/service/StaffingService.java` — builds the tree.
- `src/main/java/com/softility/omivertex/web/StaffingController.java` — `GET /api/v1/staffing`.

**Backend — tests:**
- `src/test/java/com/softility/omivertex/api/DataTransferApiTest.java` (extend — import capacity)
- `src/test/java/com/softility/omivertex/api/DashboardApiTest.java` (extend — headcount split)
- `src/test/java/com/softility/omivertex/api/StaffingApiTest.java` (create)

**Frontend — create:**
- `frontend/src/components/AllocationForm.jsx` — shared assign/edit form fields.
- `frontend/src/pages/Staffing.jsx` — drill-down page.

**Frontend — modify:**
- `frontend/src/pages/Allocations.jsx` — use `AllocationForm`.
- `frontend/src/pages/Profile.jsx` — End + Assign actions on Engagement History.
- `frontend/src/pages/Dashboard.jsx` — B/NB split + client links.
- `frontend/src/App.jsx` — Staffing route + nav entry.

**Docs:** `docs/TECHNICAL.md`, `docs/TODO.md`.

---

## Task 1: Import respects the capacity guard

**Files:**
- Modify: `src/main/java/com/softility/omivertex/service/AllocationService.java:111`
- Modify: `src/main/java/com/softility/omivertex/service/ImportService.java`
- Test: `src/test/java/com/softility/omivertex/api/DataTransferApiTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `DataTransferApiTest.java` (uses existing `ApiTestBase` helpers `associate`/`client`/`project`/`allocation`; import matches associates by `EmailNaming.forName`, so "Priya Sharma" must be seeded as `priya.sharma@softility.com`):

```java
    @Test
    void import_overCapacityRow_isRejectedWithError_associateStillImported() throws Exception {
        // Priya is already 100% allocated on an open-ended project.
        var acme = client("Acme Corp");
        var proj = project("ACM-1", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya.sharma@softility.com",
                com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(priya, proj, true);

        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project
                Priya Sharma,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                Arjun Rao,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file))
                .andExpect(status().isOk())
                // Arjun's allocation is created; Priya's is rejected with a row error
                .andExpect(jsonPath("$.allocationsCreated").value(1))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("maximum is 100%")));

        // Priya still has exactly her original single allocation
        org.junit.jupiter.api.Assertions.assertEquals(1,
                allocationRepository.findByAssociateId(priya.getId()).size());
    }

    @Test
    void importDryRun_overCapacityRow_reportsErrorAndWritesNothing() throws Exception {
        var acme = client("Acme Corp");
        var proj = project("ACM-1", "Data Platform", acme);
        var priya = associate("Priya Sharma", "priya.sharma@softility.com",
                com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(priya, proj, true);

        String csv = """
                ASSOCIATE NAME,COMPANY,LOCATION,CUSTOMER,BILLABLE,Project
                Priya Sharma,Softility,OFFSHORE,Meridian Health,B,Patient Portal
                """;
        var file = new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/data/import").file(file).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("maximum is 100%")));

        // dry run wrote nothing: still 1 allocation, no Meridian client
        org.junit.jupiter.api.Assertions.assertEquals(1, allocationRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, clientRepository.count());
    }
```

Note: `allocationRepository.findByAssociateId` must exist on `AllocationRepository` (it does — `AllocationService.assertCapacity` already uses it).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=DataTransferApiTest#import_overCapacityRow_isRejectedWithError_associateStillImported+importDryRun_overCapacityRow_reportsErrorAndWritesNothing`
Expected: FAIL — `allocationsCreated` is 2 (not 1) and `errors` is empty; the import currently allows the over-allocation.

- [ ] **Step 3: Widen the guard's visibility**

In `AllocationService.java`, change the `assertCapacity` signature from `private` to package-private and document why (line 111):

```java
    /**
     * An associate is 100% capacity: the sum of allocation percentages across
     * date-overlapping allocations may never exceed it. Package-private so
     * ImportService applies the SAME rule (one implementation, no copies).
     */
    void assertCapacity(Associate associate, Long excludeAllocationId, int newPercent,
                        java.time.LocalDate newStart, java.time.LocalDate newEnd) {
```

(Method body unchanged.)

- [ ] **Step 4: Call the guard from ImportService**

In `ImportService.java`:

Add the field and constructor parameter (after `spreadsheetParser`):

```java
    private final AllocationService allocationService;
```

```java
    public ImportService(ClientRepository clients, ProjectRepository projects,
                         AssociateRepository associates, AllocationRepository allocations,
                         AuditService auditService, PlatformTransactionManager transactionManager,
                         SkillCategoryRepository skillCategories, SkillRepository skills,
                         AssociateSkillRepository associateSkills, CertificationRepository certifications,
                         SpreadsheetParser spreadsheetParser, AllocationService allocationService) {
        ...existing assignments...
        this.allocationService = allocationService;
    }
```

Replace the allocation-creation block in `processRows` (currently lines 298-309):

```java
                if (allocations.existsByAssociateIdAndProjectIdAndEndDateIsNull(associate.getId(), project.getId())) {
                    skipped++;
                } else {
                    // Same capacity rule as the assign flow: imported allocations are
                    // 100%, starting today, open-ended. Throws ConflictException on
                    // over-allocation, which the row catch below turns into a row error.
                    allocationService.assertCapacity(associate, null, 100, LocalDate.now(), null);
                    Allocation allocation = new Allocation();
                    allocation.setAssociate(associate);
                    allocation.setProject(project);
                    allocation.setBillable(billable);
                    allocation.setAllocationPercent(100);
                    allocation.setStartDate(LocalDate.now());
                    allocations.save(allocation);
                    allocationsCreated++;
                }
```

(The existing `catch (Exception ex) { errors.add("Row " + rowNumber + ": " + ex.getMessage()); }` already turns the `ConflictException` into the row error — no new catch needed.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=DataTransferApiTest`
Expected: PASS (all existing DataTransfer tests plus the 2 new ones).

- [ ] **Step 6: Run the full suite and commit**

Run: `./mvnw spotless:apply && ./mvnw test`
Expected: BUILD SUCCESS, 142 tests (140 + 2), 0 failures.

```bash
git add src/main/java/com/softility/omivertex/service/AllocationService.java \
  src/main/java/com/softility/omivertex/service/ImportService.java \
  src/test/java/com/softility/omivertex/api/DataTransferApiTest.java
git commit -m "fix: import enforces the 100% capacity guard per row

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Shared AllocationForm component

> Frontend task — verified by `npm run build` (runs Prettier + ESLint) and manual smoke.

**Files:**
- Create: `frontend/src/components/AllocationForm.jsx`
- Modify: `frontend/src/pages/Allocations.jsx`

- [ ] **Step 1: Create the shared form** `frontend/src/components/AllocationForm.jsx`

Extracted from the modal body of `Allocations.jsx`. The associate picker renders only when `searchAssociates` is provided (the profile passes a fixed associate instead).

```jsx
import Field from './Field.jsx';
import SearchSelect from './SearchSelect.jsx';

/**
 * Shared allocation form fields, used by the Allocations page (with an associate
 * picker) and the Profile "Assign to Project" dialog (associate fixed by the page).
 *
 * form:       { associateId, companyId, projectId, billable, allocationPercent, startDate, endDate }
 * setField:   (key, value) => void
 * setFields:  (partial) => void   — for updates that touch two keys at once
 * errors:     { field: message } from the API
 * projects:   full project list [{ id, name, clientId, clientName }]
 * searchAssociates: optional async (q) => [{value,label}] — omit to hide the picker
 * showProjectPicker: hide company/project fields when editing an existing allocation
 */
export default function AllocationForm({
  form,
  setField,
  setFields,
  errors,
  projects,
  searchAssociates,
  showProjectPicker = true,
}) {
  // Company picker options: distinct clients that actually have projects.
  const companies = Array.from(
    new Map((projects || []).map((p) => [p.clientId, p.clientName])).entries()
  )
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label));

  // Project picker options: only the chosen company's projects.
  const companyProjects = (projects || [])
    .filter((p) => String(p.clientId) === String(form.companyId))
    .map((p) => ({ value: p.id, label: p.name }));

  return (
    <div className="form-grid">
      {searchAssociates && (
        <Field label="Associate" required error={errors.associateId} full>
          <SearchSelect
            onSearch={searchAssociates}
            value={form.associateId}
            onChange={(v) => setField('associateId', v)}
            placeholder="Search associates by name or email…"
            invalid={!!errors.associateId}
          />
        </Field>
      )}
      {showProjectPicker && (
        <>
          <Field label="Company" required full>
            <SearchSelect
              options={companies}
              value={form.companyId}
              onChange={(v) => setFields({ companyId: v, projectId: '' })}
              placeholder="Search company…"
            />
          </Field>
          <Field label="Project" required error={errors.projectId} full>
            <SearchSelect
              options={companyProjects}
              value={form.projectId}
              onChange={(v) => setField('projectId', v)}
              placeholder={form.companyId ? 'Search project…' : 'Select a company first'}
              invalid={!!errors.projectId}
            />
          </Field>
        </>
      )}
      <Field label="Allocation %" required error={errors.allocationPercent}>
        <input
          type="number"
          min="1"
          max="100"
          value={form.allocationPercent}
          onChange={(e) => setField('allocationPercent', e.target.value)}
          className={errors.allocationPercent ? 'invalid' : ''}
        />
      </Field>
      <div className="checkbox-field">
        <input
          id="billable"
          type="checkbox"
          checked={form.billable}
          onChange={(e) => setField('billable', e.target.checked)}
        />
        <label htmlFor="billable">Billable engagement</label>
      </div>
      <Field label="Start date" required error={errors.startDate}>
        <input
          type="date"
          value={form.startDate}
          onChange={(e) => setField('startDate', e.target.value)}
          className={errors.startDate ? 'invalid' : ''}
        />
      </Field>
      <Field label="End date (roll-off)" error={errors.endDate}>
        <input
          type="date"
          value={form.endDate}
          onChange={(e) => setField('endDate', e.target.value)}
        />
      </Field>
    </div>
  );
}
```

- [ ] **Step 2: Use it in `Allocations.jsx`**

Add the import:

```jsx
import AllocationForm from '../components/AllocationForm.jsx';
```

Replace the modal body — everything between `{errors._general && <div className="form-alert">{errors._general}</div>}` and the modal's closing `</Modal>` (the whole `<div className="form-grid">…</div>` block) — with:

```jsx
          {errors._general && <div className="form-alert">{errors._general}</div>}
          <AllocationForm
            form={editing.form}
            setField={set}
            setFields={(partial) => setEditing((e) => ({ ...e, form: { ...e.form, ...partial } }))}
            errors={errors}
            projects={projects}
            searchAssociates={editing.id ? undefined : searchAssociates}
            showProjectPicker={!editing.id}
          />
```

Then delete the now-unused pieces of `Allocations.jsx`: the `companies` and `companyProjects` constants, and the `Field`/`SearchSelect` imports **only if** no longer referenced (the toolbar still uses `SearchSelect` — keep that import; `Field` becomes unused — remove it).

- [ ] **Step 3: Verify it builds, then smoke-test**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds, 0 ESLint errors.

Manual smoke: Allocations page → "Assign Associate" modal shows associate/company/project pickers; Edit modal shows only %/billable/dates. Both save.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/AllocationForm.jsx frontend/src/pages/Allocations.jsx
git commit -m "refactor: extract shared AllocationForm from the Allocations page

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: End + Assign on the Associate Profile

> Frontend task — no new backend; reuses `PUT /allocations/{id}` and `POST /allocations` (both guard-protected).

**Files:**
- Modify: `frontend/src/pages/Profile.jsx`

- [ ] **Step 1: Load reload + projects, add state**

In `Profile.jsx`, change the allocations `useLoad` to also take `reload` (currently it doesn't):

```jsx
  const {
    data: allocations,
    loading: loadAllocs,
    reload: reloadAllocs,
  } = useLoad(() => api.list('allocations', { associateId: id }), [id]);
  // Loaded for the Assign dialog's company→project pickers.
  const { data: projects } = useLoad(() => api.list('projects'), []);
```

Add the dialog state next to the résumé state:

```jsx
  const [ending, setEnding] = useState(null); // allocation row being ended, + endDate
  const [assigning, setAssigning] = useState(false);
  const [assignForm, setAssignForm] = useState(null);
  const [allocErrors, setAllocErrors] = useState({});
  const [savingAlloc, setSavingAlloc] = useState(false);
```

Add the import:

```jsx
import AllocationForm from '../components/AllocationForm.jsx';
```

- [ ] **Step 2: Add the handlers** (place after `handleReviewSkills`)

```jsx
  const todayStr = () => new Date().toISOString().slice(0, 10);

  const openEnd = (row) => {
    setAllocErrors({});
    setEnding({ row, endDate: todayStr() });
  };

  // Ends a current allocation: existing values + the chosen end date, through the
  // normal update endpoint. The row flips to "Ended" and stays in history.
  const handleEndAllocation = async () => {
    const { row, endDate } = ending;
    if (!endDate || endDate < row.startDate) {
      setAllocErrors({ endDate: 'End date must be on or after the start date' });
      return;
    }
    setSavingAlloc(true);
    setAllocErrors({});
    try {
      await api.update('allocations', row.id, {
        billable: row.billable,
        allocationPercent: row.allocationPercent,
        startDate: row.startDate,
        endDate,
      });
      showToast(`Ended allocation on ${row.projectName}`);
      setEnding(null);
      reloadAllocs();
      reloadAssoc(); // bench/billable badges may change
    } catch (err) {
      setAllocErrors({ _general: err.message });
    } finally {
      setSavingAlloc(false);
    }
  };

  const openAssign = () => {
    setAllocErrors({});
    setAssignForm({
      companyId: '',
      projectId: '',
      billable: true,
      allocationPercent: 100,
      startDate: todayStr(),
      endDate: '',
    });
    setAssigning(true);
  };

  // Assigns THIS associate to a project. The server's capacity guard applies —
  // someone still at 100% gets the clear "maximum is 100%" error in the form.
  const handleAssign = async () => {
    setSavingAlloc(true);
    setAllocErrors({});
    try {
      await api.create('allocations', {
        associateId: Number(id),
        projectId: assignForm.projectId === '' ? null : Number(assignForm.projectId),
        billable: assignForm.billable,
        allocationPercent: Number(assignForm.allocationPercent),
        startDate: assignForm.startDate,
        endDate: assignForm.endDate || null,
      });
      showToast(`Assigned ${associate.name} to a new project`);
      setAssigning(false);
      reloadAllocs();
      reloadAssoc();
    } catch (err) {
      setAllocErrors({
        ...err.fieldErrors,
        _general: Object.keys(err.fieldErrors || {}).length ? null : err.message,
      });
    } finally {
      setSavingAlloc(false);
    }
  };
```

- [ ] **Step 3: Add the Assign button and per-row End button to the history card**

Change the Engagement History heading block to include the button:

```jsx
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}
        >
          <h3 style={{ margin: 0 }}>Allocation &amp; Engagement History</h3>
          {canEdit && (
            <button className="btn btn-primary btn-sm" onClick={openAssign}>
              <Icon name="plus" size={14} /> Assign to Project
            </button>
          )}
        </div>
```

In the history table, add a header cell after Status — `{canEdit && <th style={{ width: '70px' }} />}` — and in the row, after the Status `<td>`:

```jsx
                      {canEdit && (
                        <td className="actions">
                          {isCurrent && (
                            <button
                              className="btn btn-ghost btn-sm"
                              onClick={() => openEnd(a)}
                              aria-label={`End allocation on ${a.projectName}`}
                            >
                              End
                            </button>
                          )}
                        </td>
                      )}
```

(`isCurrent` is already computed per row in the existing map.)

- [ ] **Step 4: Add the two modals** (before the closing `</div>` of the page, next to the other modals)

```jsx
      {/* End Allocation Modal */}
      {ending && (
        <Modal
          title={`End allocation · ${ending.row.projectName}`}
          onClose={() => setEnding(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setEnding(null)}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={handleEndAllocation}
                disabled={savingAlloc}
              >
                {savingAlloc ? 'Ending…' : 'End allocation'}
              </button>
            </>
          }
        >
          {allocErrors._general && <div className="form-alert">{allocErrors._general}</div>}
          <p className="cell-sub" style={{ marginTop: 0 }}>
            The allocation stays in the history as “Ended” — nothing is deleted. Ending
            frees up capacity so the associate can be assigned to a new project.
          </p>
          <div className="form-grid">
            <Field label="End date" required error={allocErrors.endDate}>
              <input
                type="date"
                min={ending.row.startDate}
                value={ending.endDate}
                onChange={(e) => setEnding((s) => ({ ...s, endDate: e.target.value }))}
                className={allocErrors.endDate ? 'invalid' : ''}
              />
            </Field>
          </div>
        </Modal>
      )}

      {/* Assign to Project Modal */}
      {assigning && assignForm && (
        <Modal
          title={`Assign ${associate.name} to a project`}
          onClose={() => setAssigning(false)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setAssigning(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={handleAssign} disabled={savingAlloc}>
                {savingAlloc ? 'Assigning…' : 'Assign'}
              </button>
            </>
          }
        >
          {allocErrors._general && <div className="form-alert">{allocErrors._general}</div>}
          <AllocationForm
            form={assignForm}
            setField={(k, v) => setAssignForm((f) => ({ ...f, [k]: v }))}
            setFields={(partial) => setAssignForm((f) => ({ ...f, ...partial }))}
            errors={allocErrors}
            projects={projects}
          />
        </Modal>
      )}
```

(No `searchAssociates` prop → the associate picker is hidden; the associate is fixed to this profile.)

- [ ] **Step 5: Verify build + smoke-test**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds, 0 ESLint errors.

Manual smoke (app running): on an over-allocated associate (Priya) — End one allocation (row flips to Ended, stays listed), then Assign to Project succeeds; trying to Assign while still at 100% shows the "maximum is 100%" error in the dialog. Viewer sees neither button.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Profile.jsx
git commit -m "feat: end & assign allocations from the associate profile

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Dashboard per-client billable/non-billable split

**Files:**
- Modify: `src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java:23`
- Modify: `src/main/java/com/softility/omivertex/service/DashboardService.java:82-89`
- Modify: `frontend/src/pages/Dashboard.jsx`
- Test: `src/test/java/com/softility/omivertex/api/DashboardApiTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DashboardApiTest.java`:

```java
    @Test
    void clientHeadcounts_splitBillableAndNonBillable() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var p2 = project("ACM-2", "Support Desk", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        var qa = associate("Vikram Das", "vikram@softility.com", com.softility.omivertex.domain.WorkMode.OFFSHORE);
        allocation(dev, p1, true);   // billable on Acme
        allocation(qa, p2, false);   // non-billable on Acme

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientHeadcounts[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.clientHeadcounts[0].clientId").value(acme.getId()))
                .andExpect(jsonPath("$.clientHeadcounts[0].headcount").value(2))
                .andExpect(jsonPath("$.clientHeadcounts[0].billable").value(1))
                .andExpect(jsonPath("$.clientHeadcounts[0].nonBillable").value(1));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DashboardApiTest#clientHeadcounts_splitBillableAndNonBillable`
Expected: FAIL — no `clientId`/`billable`/`nonBillable` fields on the JSON.

- [ ] **Step 3: Extend the DTO**

In `DashboardSummaryResponse.java`, replace the `ClientHeadcount` record:

```java
    /**
     * Distinct current associates per client, split by billing. An associate counts
     * as billable for a client when ANY of their current allocations there is
     * billable; headcount == billable + nonBillable.
     */
    public record ClientHeadcount(Long clientId, String clientName, long headcount,
                                  long billable, long nonBillable) {
    }
```

- [ ] **Step 4: Compute the split in `DashboardService`**

Replace the `byClient`/`headcounts` block (currently lines 82-89):

```java
        // Distinct associates per client, split billable/non-billable. Billable wins:
        // one billable allocation under the client makes the person billable there.
        record ClientKey(Long id, String name) {}
        Map<ClientKey, List<Allocation>> byClient = current.stream().collect(Collectors.groupingBy(
                a -> new ClientKey(a.getProject().getClient().getId(), a.getProject().getClient().getName())));
        List<ClientHeadcount> headcounts = byClient.entrySet().stream()
                .map(e -> {
                    Set<Long> billableHere = e.getValue().stream()
                            .filter(Allocation::isBillable)
                            .map(a -> a.getAssociate().getId())
                            .collect(Collectors.toSet());
                    Set<Long> everyone = e.getValue().stream()
                            .map(a -> a.getAssociate().getId())
                            .collect(Collectors.toSet());
                    long nonBillable = everyone.stream().filter(id -> !billableHere.contains(id)).count();
                    return new ClientHeadcount(e.getKey().id(), e.getKey().name(),
                            everyone.size(), billableHere.size(), nonBillable);
                })
                .sorted(Comparator.comparingLong(ClientHeadcount::headcount).reversed()
                        .thenComparing(ClientHeadcount::clientName))
                .toList();
```

- [ ] **Step 5: Run the tests**

Run: `./mvnw test -Dtest=DashboardApiTest`
Expected: PASS — including the pre-existing assertions (`clientName`, `headcount(2)`), which are unchanged.

- [ ] **Step 6: Update the dashboard card**

In `frontend/src/pages/Dashboard.jsx`, replace the list-mode rank row (the `s.clientHeadcounts.map((c, i) => (...))` block around line 338):

```jsx
            s.clientHeadcounts.map((c, i) => (
              <div
                className="rank-row"
                key={c.clientName}
                style={{ cursor: 'pointer' }}
                onClick={() => (window.location.hash = `/staffing?clientId=${c.clientId}`)}
                title={`Open staffing for ${c.clientName}`}
              >
                <span className="rank-name">{c.clientName}</span>
                <span className="rank-count">
                  {c.headcount} — {c.billable} billable / {c.nonBillable} non-billable
                </span>
                <div
                  className="rank-track"
                  role="img"
                  aria-label={`${c.clientName}: ${c.headcount} associates, ${c.billable} billable, ${c.nonBillable} non-billable`}
                >
                  <div
                    className="rank-fill"
                    style={{
                      width: `${(c.headcount / maxHeadcount) * 100}%`,
                      background: `var(--chart-${(i % 5) + 1})`,
                    }}
                  />
                </div>
              </div>
            ))
```

(The chart mode continues to plot `headcount` — unchanged. The `#/staffing` route arrives in Task 6; the link is inert until then, which is fine within the same feature branch.)

- [ ] **Step 7: Build, full suite, commit**

Run: `cd frontend && npm run format && npm run build` → succeeds.
Run: `./mvnw spotless:apply && ./mvnw test` → all green.

```bash
git add src/main/java/com/softility/omivertex/web/dto/DashboardSummaryResponse.java \
  src/main/java/com/softility/omivertex/service/DashboardService.java \
  src/test/java/com/softility/omivertex/api/DashboardApiTest.java \
  frontend/src/pages/Dashboard.jsx
git commit -m "feat: billable/non-billable split per client on the dashboard

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Staffing endpoint (GET /api/v1/staffing)

**Files:**
- Create: `src/main/java/com/softility/omivertex/web/dto/StaffingDtos.java`
- Create: `src/main/java/com/softility/omivertex/service/StaffingService.java`
- Create: `src/main/java/com/softility/omivertex/web/StaffingController.java`
- Test: `src/test/java/com/softility/omivertex/api/StaffingApiTest.java`

- [ ] **Step 1: Write the failing test** `src/test/java/com/softility/omivertex/api/StaffingApiTest.java`

```java
package com.softility.omivertex.api;

import com.softility.omivertex.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffingApiTest extends ApiTestBase {

    @Test
    void staffing_buildsClientProjectAssociateTree_withBillableSplit() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var qa = associate("Vikram Das", "vikram@softility.com", WorkMode.OFFSHORE);
        allocation(dev, p1, true);   // billable
        allocation(qa, p1, false);   // non-billable, same project

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].billable").value(1))
                .andExpect(jsonPath("$[0].nonBillable").value(1))
                .andExpect(jsonPath("$[0].projects", hasSize(1)))
                .andExpect(jsonPath("$[0].projects[0].projectName").value("Data Platform"))
                .andExpect(jsonPath("$[0].projects[0].projectCode").value("ACM-1"))
                .andExpect(jsonPath("$[0].projects[0].billable").value(1))
                .andExpect(jsonPath("$[0].projects[0].nonBillable").value(1))
                .andExpect(jsonPath("$[0].projects[0].associates", hasSize(2)))
                // associates sorted by name: Asha before Vikram
                .andExpect(jsonPath("$[0].projects[0].associates[0].name").value("Asha Iyer"))
                .andExpect(jsonPath("$[0].projects[0].associates[0].billable").value(true))
                .andExpect(jsonPath("$[0].projects[0].associates[1].name").value("Vikram Das"))
                .andExpect(jsonPath("$[0].projects[0].associates[1].billable").value(false));
    }

    @Test
    void staffing_countsAssociateOncePerClient_billableWins() throws Exception {
        // Asha is billable on one Acme project and non-billable on another:
        // client level counts her ONCE, as billable.
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var p2 = project("ACM-2", "Support Desk", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var a1 = allocation(dev, p1, true);
        a1.setAllocationPercent(50);
        allocationRepository.save(a1);
        var a2 = allocation(dev, p2, false);
        a2.setAllocationPercent(50);
        allocationRepository.save(a2);

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(jsonPath("$[0].billable").value(1))
                .andExpect(jsonPath("$[0].nonBillable").value(0))
                .andExpect(jsonPath("$[0].projects", hasSize(2)));
    }

    @Test
    void staffing_excludesEndedAllocations() throws Exception {
        var acme = client("Acme Corp");
        var p1 = project("ACM-1", "Data Platform", acme);
        var dev = associate("Asha Iyer", "asha@softility.com", WorkMode.OFFSHORE);
        var ended = allocation(dev, p1, true);
        ended.setEndDate(LocalDate.now().minusDays(10));
        allocationRepository.save(ended);

        mockMvc.perform(get("/api/v1/staffing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void staffing_isReadableByViewer() throws Exception {
        mockMvc.perform(get("/api/v1/staffing")).andExpect(status().isOk());
    }
}
```

Note: the capacity guard is irrelevant here — these seed via repositories, and the two 50% allocations in test 2 are legal anyway.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=StaffingApiTest`
Expected: FAIL — 404s (no `/api/v1/staffing` mapping).

- [ ] **Step 3: Create the DTOs** `src/main/java/com/softility/omivertex/web/dto/StaffingDtos.java`

```java
package com.softility.omivertex.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Company → project → associates staffing tree, built from CURRENT allocations. */
public final class StaffingDtos {

    private StaffingDtos() {
    }

    public record StaffedAssociate(Long associateId, String name, String designation,
                                   int allocationPercent, boolean billable, LocalDate startDate) {
    }

    public record StaffedProject(Long projectId, String projectName, String projectCode,
                                 long billable, long nonBillable,
                                 List<StaffedAssociate> associates) {
    }

    public record StaffedClient(Long clientId, String clientName,
                                long billable, long nonBillable,
                                List<StaffedProject> projects) {
    }
}
```

- [ ] **Step 4: Create the service** `src/main/java/com/softility/omivertex/service/StaffingService.java`

```java
package com.softility.omivertex.service;

import com.softility.omivertex.domain.Allocation;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedAssociate;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedClient;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the company → project → associates staffing tree from CURRENT allocations.
 * Client-level billable counting matches the dashboard rule: one billable allocation
 * under the client makes the person billable there; each person counts once per client.
 */
@Service
@Transactional(readOnly = true)
public class StaffingService {

    private final AllocationRepository allocationRepository;

    public StaffingService(AllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    public List<StaffedClient> staffing() {
        List<Allocation> current = allocationRepository.findAllWithDetails().stream()
                .filter(Allocation::isCurrent)
                .toList();

        Map<Long, List<Allocation>> byClient = current.stream()
                .collect(Collectors.groupingBy(a -> a.getProject().getClient().getId()));

        return byClient.values().stream()
                .map(this::toClient)
                .sorted(Comparator.comparingLong(
                                (StaffedClient c) -> c.billable() + c.nonBillable()).reversed()
                        .thenComparing(StaffedClient::clientName))
                .toList();
    }

    private StaffedClient toClient(List<Allocation> clientAllocations) {
        var client = clientAllocations.get(0).getProject().getClient();
        Set<Long> billableHere = clientAllocations.stream()
                .filter(Allocation::isBillable)
                .map(a -> a.getAssociate().getId())
                .collect(Collectors.toSet());
        long everyone = clientAllocations.stream()
                .map(a -> a.getAssociate().getId()).distinct().count();

        List<StaffedProject> projects = clientAllocations.stream()
                .collect(Collectors.groupingBy(a -> a.getProject().getId()))
                .values().stream()
                .map(this::toProject)
                .sorted(Comparator.comparing(StaffedProject::projectName))
                .toList();

        return new StaffedClient(client.getId(), client.getName(),
                billableHere.size(), everyone - billableHere.size(), projects);
    }

    private StaffedProject toProject(List<Allocation> projectAllocations) {
        var project = projectAllocations.get(0).getProject();
        List<StaffedAssociate> associates = projectAllocations.stream()
                .map(a -> new StaffedAssociate(a.getAssociate().getId(), a.getAssociate().getName(),
                        a.getAssociate().getDesignation(), a.getAllocationPercent(),
                        a.isBillable(), a.getStartDate()))
                .sorted(Comparator.comparing(StaffedAssociate::name))
                .toList();
        long billable = associates.stream().filter(StaffedAssociate::billable)
                .map(StaffedAssociate::associateId).distinct().count();
        long total = associates.stream().map(StaffedAssociate::associateId).distinct().count();
        return new StaffedProject(project.getId(), project.getName(), project.getCode(),
                billable, total - billable, associates);
    }
}
```

- [ ] **Step 5: Create the controller** `src/main/java/com/softility/omivertex/web/StaffingController.java`

```java
package com.softility.omivertex.web;

import com.softility.omivertex.service.StaffingService;
import com.softility.omivertex.web.dto.StaffingDtos.StaffedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staffing")
public class StaffingController {

    private final StaffingService staffingService;

    public StaffingController(StaffingService staffingService) {
        this.staffingService = staffingService;
    }

    /** Company → project → associates tree from current allocations. Admin + viewer (GET). */
    @GetMapping
    public List<StaffedClient> staffing() {
        return staffingService.staffing();
    }
}
```

(No security changes: `GET /api/v1/**` is already admin+viewer.)

- [ ] **Step 6: Run the tests**

Run: `./mvnw test -Dtest=StaffingApiTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Full suite + commit**

Run: `./mvnw spotless:apply && ./mvnw test` → all green.

```bash
git add src/main/java/com/softility/omivertex/web/dto/StaffingDtos.java \
  src/main/java/com/softility/omivertex/service/StaffingService.java \
  src/main/java/com/softility/omivertex/web/StaffingController.java \
  src/test/java/com/softility/omivertex/api/StaffingApiTest.java
git commit -m "feat: GET /api/v1/staffing — client/project/associate staffing tree

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Staffing page + route

> Frontend task — verified by build + manual smoke.

**Files:**
- Create: `frontend/src/pages/Staffing.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: Create the page** `frontend/src/pages/Staffing.jsx`

Deep link: `#/staffing?clientId=5` expands that client (the dashboard links this way). Sections are collapsible; the deep-linked or first client starts open.

```jsx
import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Badge from '../components/Badge.jsx';
import Icon from '../components/Icon.jsx';

const getParam = (name) => {
  const searchPart = window.location.hash.split('?')[1];
  if (!searchPart) return '';
  return new URLSearchParams(searchPart).get(name) || '';
};

export default function Staffing() {
  const { data: clients, loading } = useLoad(() => api.list('staffing'), []);
  // Which client sections are expanded. Seeded from the ?clientId= deep link.
  const [open, setOpen] = useState(() => {
    const fromLink = getParam('clientId');
    return fromLink ? { [fromLink]: true } : null; // null = "open the first one"
  });

  if (loading) {
    return (
      <div>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  if (!clients || clients.length === 0) {
    return (
      <div className="card">
        <div className="empty-state">
          <Icon name="inbox" size={40} />
          <p>No current allocations. Assign associates to projects first.</p>
        </div>
      </div>
    );
  }

  const isOpen = (clientId) =>
    open === null ? String(clientId) === String(clients[0].clientId) : !!open[clientId];
  const toggle = (clientId) =>
    setOpen((prev) => {
      const base = prev === null ? { [clients[0].clientId]: true } : prev;
      return { ...base, [clientId]: !base[clientId] };
    });

  return (
    <div style={{ display: 'grid', gap: '16px' }}>
      {clients.map((c) => (
        <div className="card" key={c.clientId} style={{ padding: 0, overflow: 'hidden' }}>
          <button
            type="button"
            onClick={() => toggle(c.clientId)}
            aria-expanded={isOpen(c.clientId)}
            style={{
              display: 'flex',
              width: '100%',
              alignItems: 'center',
              gap: '12px',
              padding: '16px 20px',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <span aria-hidden="true" style={{ fontSize: '12px' }}>
              {isOpen(c.clientId) ? '▾' : '▸'}
            </span>
            <h3 style={{ margin: 0, flexGrow: 1 }}>{c.clientName}</h3>
            <Badge value="Billable" label={`${c.billable} billable`} tone="green" />
            <Badge value="Non-billable" label={`${c.nonBillable} non-billable`} tone="amber" />
          </button>

          {isOpen(c.clientId) && (
            <div style={{ padding: '0 20px 20px', display: 'grid', gap: '16px' }}>
              {c.projects.map((p) => (
                <div key={p.projectId}>
                  <div
                    className="cell-sub"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      fontWeight: 600,
                      margin: '8px 0',
                    }}
                  >
                    <span>
                      {p.projectName} <span style={{ fontWeight: 400 }}>· {p.projectCode}</span>
                    </span>
                    <span style={{ fontWeight: 400 }}>
                      {p.billable} billable / {p.nonBillable} non-billable
                    </span>
                  </div>
                  <div
                    className="table-wrap"
                    style={{ margin: 0, boxShadow: 'none', border: '1px solid var(--color-border)' }}
                  >
                    <table style={{ fontSize: '13px' }}>
                      <thead>
                        <tr>
                          <th>Associate</th>
                          <th>Designation</th>
                          <th>Allocation</th>
                          <th>Billing</th>
                          <th>Since</th>
                        </tr>
                      </thead>
                      <tbody>
                        {p.associates.map((a) => (
                          <tr key={`${p.projectId}-${a.associateId}`}>
                            <td>
                              <a className="cell-main" href={`#/associates/${a.associateId}`}>
                                {a.name}
                              </a>
                            </td>
                            <td>{a.designation || '—'}</td>
                            <td>{a.allocationPercent}%</td>
                            <td>
                              <Badge value={a.billable ? 'Billable' : 'Non-billable'} />
                            </td>
                            <td>{a.startDate}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
```

Note: `Icon.jsx` has no chevron icons (verified), so the expand indicator is a plain text caret (▸/▾) — do not add new icons for this.

- [ ] **Step 2: Register the route + nav**

In `frontend/src/App.jsx`:

Add the import:

```jsx
import Staffing from './pages/Staffing.jsx';
```

Add to `ROUTES` (after the `allocations` entry):

```jsx
  {
    path: 'staffing',
    label: 'Staffing',
    icon: 'users',
    component: Staffing,
    sub: 'Billable and non-billable staffing by company and project',
  },
```

Add `'staffing'` to the Delivery section of `NAV_SECTIONS`:

```jsx
  { label: 'Delivery', items: ['clients', 'projects', 'allocations', 'staffing', 'demand'] },
```

(Route matching already strips `?clientId=` — `baseRoute = route.split('?')[0]` — so the deep link resolves to the `staffing` route with no further App.jsx changes.)

- [ ] **Step 3: Build + smoke-test**

Run: `cd frontend && npm run format && npm run build`
Expected: build succeeds, 0 ESLint errors.

Manual smoke: sidebar shows Staffing (admin AND viewer); page lists companies with B/NB badges; expanding shows projects with counts and associate tables; clicking a dashboard client row lands here with that client expanded; associate names link to profiles.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Staffing.jsx frontend/src/App.jsx
git commit -m "feat: staffing drill-down page (company -> project -> associates)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Docs, graph refresh, final verification

**Files:**
- Modify: `docs/TECHNICAL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Update `docs/TECHNICAL.md`**

- In the import section: imported allocations now enforce the 100% capacity guard; over-capacity rows are reported as row errors (associate still imported).
- New endpoint: `GET /api/v1/staffing` → `[{clientId, clientName, billable, nonBillable, projects:[{projectId, projectName, projectCode, billable, nonBillable, associates:[{associateId, name, designation, allocationPercent, billable, startDate}]}]}]`, current allocations only, admin+viewer.
- `DashboardSummaryResponse.ClientHeadcount` now `{clientId, clientName, headcount, billable, nonBillable}`.
- Profile page: End (sets endDate via `PUT /allocations/{id}`) and Assign to Project (via `POST /allocations`) actions; history rows are never deleted on end.

- [ ] **Step 2: Update `docs/TODO.md`**

Under resolved decisions: import capacity rule = reject row with error (chosen over auto-ending the older allocation or importing at 0%); client-level billable counting = "billable wins" per client; no auto-migration of pre-existing over-allocated data (fixed manually via profile End).

- [ ] **Step 3: Final verification**

Run: `./mvnw spotless:apply && ./mvnw test` → all green (~147: 140 + 2 import + 1 dashboard + 4 staffing).
Run: `cd frontend && npm run build` → succeeds.

- [ ] **Step 4: Refresh the graphify knowledge graph** (per AGENTS.md)

```bash
$(cat graphify-out/.graphify_python 2>/dev/null || echo python3) -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"
```

- [ ] **Step 5: Commit**

```bash
git add docs/TECHNICAL.md docs/TODO.md
git commit -m "docs: capacity import rule, staffing endpoint, profile allocation actions

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review (checked against the spec)

**Spec coverage:**
- Part 1: guard shared with import (package-private, one implementation) + row error + dry-run + idempotent skip preserved → Task 1. ✅
- Part 2: End on current rows (dialog, default today, ≥ startDate), Assign with locked associate via shared `AllocationForm`, guard errors surfaced in-form, history never deleted → Tasks 2–3. ✅
- Part 3: `ClientHeadcount` split + clickable rows → Task 4; `GET /api/v1/staffing` tree, counting rules, sorting, viewer access → Task 5; Staffing page + route + nav + deep link → Task 6. ✅
- Docs + graph → Task 7. Non-goals respected (no migration, no pagination, no dedup across clients). ✅

**Placeholder scan:** none — every code step has complete code; commands have expected outcomes. ✅

**Type consistency:** `ClientHeadcount(clientId, clientName, headcount, billable, nonBillable)` used identically in DTO, service, test, and Dashboard.jsx; `StaffedClient/StaffedProject/StaffedAssociate` field names match between DTOs, service, test JSON paths, and Staffing.jsx; `AllocationForm` props (`form`, `setField`, `setFields`, `errors`, `projects`, `searchAssociates`, `showProjectPicker`) match both call sites; `assertCapacity(associate, null, 100, LocalDate.now(), null)` matches the widened signature. ✅
