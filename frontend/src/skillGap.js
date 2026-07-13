/**
 * One source of truth for how a skill-gap row reads. The Dashboard panel and the Skill
 * Reports report both render these rows, and they had already drifted — the Dashboard
 * inlined the logic while the report kept its own copy.
 *
 * gap = open seats - bench supply at the required proficiency. Note that gap === 0 has
 * two very different meanings: demand exactly met (tight), and no demand at all with
 * nobody free (fully deployed). Collapsing them into one "tight" badge, as both views
 * used to, tells a manager a skill is at knife's edge when in fact nothing is open.
 */
export function gapBadge(gap, demand) {
  if (gap > 0) return { tone: 'red', label: `short ${gap}` };
  if (gap < 0) return { tone: 'green', label: `+${-gap} spare` };
  if (demand > 0) return { tone: 'amber', label: 'tight' };
  return { tone: 'gray', label: 'fully deployed' };
}

/** Sub-line under a skill name, identical in both views. */
export function gapSummary(g) {
  return `${g.category} · ${g.demand} open · ${g.benchSupply} on bench · ${g.totalSupply} total`;
}
