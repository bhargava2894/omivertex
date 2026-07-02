const BASE = '/api/v1';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const error = new Error(body.message || 'Request failed');
    error.fieldErrors = body.fieldErrors || {};
    error.status = res.status;
    throw error;
  }
  return body;
}

export const api = {
  list: (resource, params = {}) => {
    const qs = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== '' && v != null)
    ).toString();
    return request(`/${resource}${qs ? `?${qs}` : ''}`);
  },
  get: (resource, id) => request(`/${resource}/${id}`),
  create: (resource, data) =>
    request(`/${resource}`, { method: 'POST', body: JSON.stringify(data) }),
  update: (resource, id, data) =>
    request(`/${resource}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  remove: (resource, id) => request(`/${resource}/${id}`, { method: 'DELETE' }),
  dashboard: () => request('/dashboard/summary'),
};
