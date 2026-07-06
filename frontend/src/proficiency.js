// Single source of truth for the six-level proficiency scale.
// `tone`  → Badge semantic colour (monotonic: higher proficiency reads more positive).
// `color` → distinct chart hue for stacked skill-distribution bars.
// Order matters: it is the display order and matches the backend enum ordinal.
export const PROFICIENCIES = [
  { value: 'NOVICE',          label: 'Novice',          tone: 'gray',  color: '#9ca3af' },
  { value: 'FOUNDATIONAL',    label: 'Foundational',    tone: 'amber', color: 'var(--chart-3)' },
  { value: 'INTERMEDIATE',    label: 'Intermediate',    tone: 'blue',  color: 'var(--chart-1)' },
  { value: 'FUNCTIONAL_USER', label: 'Functional User', tone: 'blue',  color: 'var(--chart-2)' },
  { value: 'ADVANCE',         label: 'Advance',         tone: 'green', color: 'var(--chart-5)' },
  { value: 'MASTERY',         label: 'Mastery',         tone: 'green', color: 'var(--chart-4)' },
];

const BY_VALUE = Object.fromEntries(PROFICIENCIES.map((p) => [p.value, p]));

export const PROF_LABELS = Object.fromEntries(PROFICIENCIES.map((p) => [p.value, p.label]));
export const PROF_TONES = Object.fromEntries(PROFICIENCIES.map((p) => [p.value, p.tone]));
export const PROF_COLORS = Object.fromEntries(PROFICIENCIES.map((p) => [p.value, p.color]));

/** Lookup with a safe fallback for unknown/null values. */
export function proficiencyInfo(value) {
  return BY_VALUE[value] || { value, label: value || '—', tone: 'gray', color: '#9ca3af' };
}
