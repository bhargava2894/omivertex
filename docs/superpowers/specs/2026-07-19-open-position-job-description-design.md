# Spec: Open Position Job Description

Adding a `jobDescription` field to `OpenPosition` ensures clarity when demand is registered, allowing resource managers to view detailed requirements for open seats directly on the Demand page and in the candidate matching view.

## User Review Required

No breaking changes are introduced. A new nullable `jobDescription` text field will be added to the entity, DTOs, and frontend components.

## Proposed Changes

### Database Migration

We will add a new Flyway migration `V2__add_job_description_to_open_positions.sql` to apply the database schema changes for both H2 and PostgreSQL.
Since `V1__baseline_schema.sql` also omitted the `required_skill` column present in the Hibernate entity `OpenPosition.java`, this migration will add both missing columns to `open_positions` for complete synchronization.

```sql
-- V2__add_job_description_to_open_positions.sql
ALTER TABLE open_positions ADD COLUMN required_skill character varying(255);
ALTER TABLE open_positions ADD COLUMN job_description text;
```

### Domain Layer

Modify [OpenPosition.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/domain/OpenPosition.java):
- Add a new private String field `jobDescription` annotated with `@Column(columnDefinition = "text")`.
- Add standard getter and setter.

### Web / DTO Layer

Modify [PositionRequest.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/PositionRequest.java):
- Add `String jobDescription` to the `PositionRequest` record.

Modify [PositionResponse.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/PositionResponse.java):
- Add `String jobDescription` to the `PositionResponse` record.
- In `PositionResponse.from(...)`, map `position.getJobDescription()` to the response record.

### Service Layer

Modify [PositionService.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/PositionService.java):
- In `apply(...)`, map `request.jobDescription()` to `position.setJobDescription(...)`.

Modify [AssistantContextBuilder.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/AssistantContextBuilder.java):
- Optionally expose the `jobDescription` in the `openPositions()` tool representation of a position if it is set.

### Frontend Layer

Modify [styles.css](file:///Users/bhargavasista/omivertex/frontend/src/styles.css):
- Update `.field input, .field select` selector to also target `.field textarea` so textareas inside form modals inherit consistent styling.
- Add `resize: vertical;` and `min-height: 100px;` to `.field textarea`.

Modify [Positions.jsx](file:///Users/bhargavasista/omivertex/frontend/src/pages/Positions.jsx):
- Add `jobDescription: ''` to `EMPTY` form definition.
- In `openEdit(...)`, map `row.jobDescription || ''`.
- In `save(...)`, include `jobDescription` in the JSON payload sent to the backend.
- Render a `<textarea>` in the edit modal inside a `Field` component:
  ```jsx
  <Field label="Job Description" error={errors.jobDescription} full>
    <textarea
      value={editing.form.jobDescription}
      onChange={(e) => set('jobDescription', e.target.value)}
      placeholder="Describe the responsibilities and project context..."
      rows={4}
    />
  </Field>
  ```
- Show a preview of the job description under the position name in the positions data table.
- Display the job description in the "Find match" candidates modal when open.

## Verification Plan

### Automated Tests
- Run backend tests to verify DTO mapping, validations, and service execution.
- Create new test cases in [PositionApiTest.java](file:///Users/bhargavasista/omivertex/src/test/java/com/softility/omivertex/api/PositionApiTest.java) to verify creating and updating positions with a job description.

### Manual Verification
- Deploy locally, open the Demand page, create a new position, enter a job description, and check that it is saved and shown in the table.
- Click "Find match" and verify the job description is shown at the top of the modal.
