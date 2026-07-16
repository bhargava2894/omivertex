/**
 * Shared display formatting for profile fields — used by both the admin-facing
 * Profile page and the associate-facing MyProfile page so the two never drift.
 */

export function workModeLabel(mode) {
  if (!mode) return null;
  return mode.charAt(0) + mode.slice(1).toLowerCase(); // ONSHORE -> Onshore
}

export function statusLabel(status) {
  return workModeLabel(status); // ACTIVE -> Active, same casing rule
}

/** "12 Mar 2023 · 3 yr 4 mo"; tenure suffix dropped when no joined date. */
export function joinedWithTenure(joinedDate) {
  if (!joinedDate) return null;
  const joined = new Date(joinedDate);
  const dateStr = joined.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
  const months = Math.max(
    0,
    (new Date().getFullYear() - joined.getFullYear()) * 12 +
      (new Date().getMonth() - joined.getMonth())
  );
  const years = Math.floor(months / 12);
  const rem = months % 12;
  const parts = [];
  if (years > 0) parts.push(`${years} yr`);
  if (rem > 0) parts.push(`${rem} mo`);
  if (years === 0 && rem === 0) parts.push('this month');
  return `${dateStr} · ${parts.join(' ')}`;
}
