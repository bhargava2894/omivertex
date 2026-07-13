# Design Spec: Position Headcount/Seats and Skill Gap Demand Calculation

## What & Why
Currently, an `OpenPosition` record is assumed to represent exactly one developer seat. If a project requires multiple developers for the same role/skill configuration (e.g., 3 Java Developers), administrators have to create 3 separate `OpenPosition` records. 

Additionally, the **Skill Gap** dashboard and report calculate demand by counting the number of requirement records (`reqs.size()`), which underestimates the total resource demand if multiple developers are needed for a single position.

We will add a `headcount` (or seats quantity) field to the `OpenPosition` model so that:
1. Users can specify how many developers are required for a position (defaulting to 1).
2. The skill demand calculation correctly sums the headcount of all matching open positions.
3. The positions list table shows the required seats/headcount.

---

## Technical Design

### 1. Database Migration
Create `src/main/resources/db/migration/V9__add_position_headcount.sql`:
```sql
ALTER TABLE open_positions ADD COLUMN headcount integer NOT NULL DEFAULT 1;
ALTER TABLE open_positions ADD CONSTRAINT open_positions_headcount_check CHECK (headcount >= 1);
```

### 2. Backend Domain Model (`OpenPosition.java`)
Modify [OpenPosition.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/domain/OpenPosition.java):
* Add `private int headcount = 1;`
* Add getter and setter.

### 3. DTO Updates
* Modify [PositionRequest.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/PositionRequest.java):
  * Add `@Min(value = 1, message = "Headcount must be at least 1") Integer headcount`
* Modify [PositionResponse.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/web/dto/PositionResponse.java):
  * Add `int headcount` field.
  * Map `position.getHeadcount()` in `PositionResponse.from(...)`.

### 4. Service Updates
* Modify [PositionService.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/PositionService.java):
  * In `apply(...)`, map the new field:
    `position.setHeadcount(request.headcount() == null ? 1 : request.headcount());`
* Modify [SkillGapService.java](file:///Users/bhargavasista/omivertex/src/main/java/com/softility/omivertex/service/SkillGapService.java):
  * Update demand calculation to sum the headcount of positions:
    ```java
    long demand = reqs.stream().mapToLong(ps -> ps.getPosition().getHeadcount()).sum();
    ```
  * Update the returning `DashboardSummaryResponse.SkillGap` constructor calls to use this `demand` value, and ensure the gap is computed as `demand - benchSupply`.

### 5. Frontend Updates
* Modify [Positions.jsx](file:///Users/bhargavasista/omivertex/frontend/src/pages/Positions.jsx):
  * Update `EMPTY` state template to include `headcount: 1`.
  * Add an input field for "Headcount" in the Modal form next to/after "Allocation %".
  * Add a new column in `DataTable` columns: `{ key: 'headcount', label: 'Seats', render: (r) => r.headcount }`.
