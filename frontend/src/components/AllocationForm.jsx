import { useId } from 'react';
import Field from './Field.jsx';
import SearchSelect from './SearchSelect.jsx';

/**
 * Shared allocation form fields, used by the Allocations page (with an associate
 * picker) and the Profile "Assign to Project" dialog (associate fixed by the page).
 *
 * form:       { associateId, companyId, projectId, billable, allocationPercent, startDate, endDate }
 *             — associateId is only read when searchAssociates is provided.
 * setField:   (key, value) => void
 * setFields:  (partial) => void   — for updates that touch two keys at once
 * errors:     { field: message } from the API
 * projects:   full project list [{ id, name, clientId, clientName }]
 * searchAssociates: optional async (q) => [{value,label}] — omit to hide the picker
 * showProjectPicker: set false to hide the company/project pickers (e.g. when editing
 *                    an existing allocation)
 */
export default function AllocationForm({
  form,
  setField,
  setFields,
  errors = {},
  projects,
  searchAssociates,
  showProjectPicker = true,
}) {
  const billableId = useId();
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
          id={billableId}
          type="checkbox"
          checked={form.billable}
          onChange={(e) => setField('billable', e.target.checked)}
        />
        <label htmlFor={billableId}>Billable engagement</label>
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
