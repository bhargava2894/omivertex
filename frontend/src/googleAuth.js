// Loads Google Identity Services (GIS) once and resolves when the API is ready.
// The client ID comes from the build-time env var VITE_GOOGLE_CLIENT_ID; when it
// is absent, Google Sign-In is simply unavailable (the UI hides the button) and
// the backend verifier also fails closed — so there is never a spoofable path.
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

const GIS_SRC = 'https://accounts.google.com/gsi/client';
let gisPromise = null;

export function loadGoogleIdentity() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google);
  if (gisPromise) return gisPromise;

  gisPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => {
      if (window.google?.accounts?.id) resolve(window.google);
      else reject(new Error('Google Identity Services failed to initialise.'));
    };
    script.onerror = () => reject(new Error('Could not load Google Identity Services.'));
    document.head.appendChild(script);
  });
  return gisPromise;
}
