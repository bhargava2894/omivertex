const tones = {
  ACTIVE: 'badge-green',
  INACTIVE: 'badge-gray',
  ON_HOLD: 'badge-amber',
  COMPLETED: 'badge-blue',
  ONSHORE: 'badge-blue',
  OFFSHORE: 'badge-amber',
  Billable: 'badge-green',
  'Non-billable': 'badge-amber',
  Bench: 'badge-red',
  Current: 'badge-green',
  Ended: 'badge-gray',
};

export default function Badge({ value }) {
  const label = String(value).replace('_', ' ');
  return <span className={`badge ${tones[value] || 'badge-gray'}`}>{label}</span>;
}
