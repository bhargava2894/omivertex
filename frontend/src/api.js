const BASE = '/api/v1';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/auth/')) {
      window.dispatchEvent(new Event('ov-unauthorized'));
    }
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
  // Downloads a generated file (xlsx/csv/pdf/docx). Fetching as a blob and saving
  // via a temporary object URL is reliable across browsers for every MIME type —
  // a plain <a download> lets some browsers try to *preview* Office files instead.
  downloadTemplate: (type) =>
    api.downloadUrl(`${BASE}/data/template?type=${encodeURIComponent(type)}`),
  exportFile: (format) =>
    api.downloadUrl(`${BASE}/data/export?format=${encodeURIComponent(format)}`),
  // Fetches a file as a blob and saves it — reliable across browsers/MIME types.
  downloadUrl: async (url) => {
    const res = await fetch(url);
    if (!res.ok) {
      if (res.status === 401) window.dispatchEvent(new Event('ov-unauthorized'));
      throw new Error(`Download failed (${res.status})`);
    }
    const disposition = res.headers.get('Content-Disposition') || '';
    const match = /filename="?([^"]+)"?/.exec(disposition);
    const filename = match ? match[1] : 'download';
    const blob = await res.blob();
    const objectUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(objectUrl);
  },
  create: (resource, data) =>
    request(`/${resource}`, { method: 'POST', body: JSON.stringify(data) }),
  update: (resource, id, data) =>
    request(`/${resource}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  remove: (resource, id) => request(`/${resource}/${id}`, { method: 'DELETE' }),
  dashboard: () => request('/dashboard/summary'),
  login: (username, password) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me'),
  googleLogin: (idToken) =>
    request('/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) }),
  listRequests: () => request('/admin/access-requests'),
  approveRequest: (id, role = 'VIEWER') =>
    request(`/admin/access-requests/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ role }),
    }),
  rejectRequest: (id) => request(`/admin/access-requests/${id}/reject`, { method: 'POST' }),
  replaceSkills: (associateId, skills) =>
    request(`/associates/${associateId}/skills`, {
      method: 'PUT',
      body: JSON.stringify({ skills }),
    }),
  getCertifications: (associateId) => request(`/associates/${associateId}/certifications`),
  addCertification: (associateId, data) =>
    request(`/associates/${associateId}/certifications`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  deleteCertification: (id) => request(`/certifications/${id}`, { method: 'DELETE' }),
};
