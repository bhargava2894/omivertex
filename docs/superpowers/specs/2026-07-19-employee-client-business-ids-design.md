# Unique Business IDs for Associates and Clients

**Date:** 2026-07-19 · **Status:** Proposed

## What & why

To align with corporate systems, the database must track external business identifiers:
1. **Employee ID** (or Associate ID) on `Associate` — e.g. "EMP001", "A-12345".
2. **Client ID** on `Client` — e.g. "CLI-001", "MERIDIAN".

These fields are unique business identifiers, distinct from database-managed autoincrement `id` fields. They are optional/nullable at the database level to ensure backward compatibility and prevent failures on existing data, but must be unique if provided.

## Data model (migration V11)

- `associates.employee_id` — nullable varchar(255) with a unique constraint.
- `clients.client_id` — nullable varchar(255) with a unique constraint.
- New migration file `V11__add_employee_id_and_client_id.sql`.

Update `Associate.java` and `Client.java` with the corresponding Java fields, annotations (`@Column(name = "employee_id", unique = true)` and `@Column(name = "client_id", unique = true)` respectively), getters, and setters.

## API changes

- `AssociateRequest` += optional `employeeId`.
- `AssociateResponse` += `employeeId`.
- `ClientRequest` += optional `clientId`.
- `ClientResponse` += `clientId`.

In `AssociateService.java` and `ClientService.java`:
- Enforce uniqueness during `create` and `update` by checking if another entity already holds the requested ID (ignoring case), throwing a `ConflictException` (409) if duplicate.
- Populate these fields on save and include them in response mappings.

## Seed data

Update `SeedDataLoader.java` to populate unique `employeeId` values (e.g. `EMP-001` through `EMP-026`) and `clientId` values (e.g. `CLI-001` through `CLI-007`) for the default dataset.

## Frontend changes

- **Clients page** (`Clients.jsx`):
  - Add `Client ID` column to the `DataTable`.
  - Add `Client ID` input field to the New/Edit Client Modal form.
- **Associates page** (`Associates.jsx`):
  - Add `Employee ID` input field to the New/Edit Associate Modal form.
  - Append `Employee ID` to the Associate's list entry (e.g., render in `cell-sub` alongside email/designation) or list as a separate column. We will render it in `cell-sub` to keep the layout clean: `[r.employeeId, r.designation, r.primarySkill, r.email]`.
- **Profile page** (`Profile.jsx`):
  - Render the `Employee ID` clearly in the Profile header (bolded or accented).
- **My Profile page** (`MyProfile.jsx`):
  - Display `Employee ID` as the first row in the "My Details" grid.

## Testing & verification

TDD per `AGENTS.md`.

- **Associate tests**:
  - Test create/update with unique `employeeId`.
  - Test duplicate `employeeId` throws `ConflictException`.
- **Client tests**:
  - Test create/update with unique `clientId`.
  - Test duplicate `clientId` throws `ConflictException`.
- **Verify build and frontend**:
  - Run `./mvnw test` to verify backend suite.
  - Run `npm run format && npm run build` to verify frontend.
- **Durable docs update**:
  - Update `docs/TECHNICAL.md` to reflect `employeeId` and `clientId` in entity/API definitions.
  - Refresh graph.
