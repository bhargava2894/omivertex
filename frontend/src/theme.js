const KEY = 'ov-theme';

export function storedTheme() {
  return localStorage.getItem(KEY) || 'system';
}

export function resolveTheme(preference) {
  if (preference === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return preference;
}

export function applyTheme(preference) {
  localStorage.setItem(KEY, preference);
  document.documentElement.dataset.theme = resolveTheme(preference);
}
