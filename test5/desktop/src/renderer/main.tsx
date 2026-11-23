// Assemblage de l'IHM : c'est ici, et seulement ici, que le noyau et les plug-ins se rencontrent.
// TypeScript vérifie que chaque plug-in attend un registre compatible avec celui du noyau.
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App, type UiPlugin } from '@test5/core-frontend';
import { checkingPlugin } from '@test5/checking-frontend';
import '@test5/core-frontend/styles.css';
import '@test5/checking-frontend/styles.css';

const plugins: readonly UiPlugin[] = [checkingPlugin];

declare global {
  interface Window {
    /** Fourni par le preload d'Electron ; absent dans un navigateur. */
    desktop?: { backendUrl: string };
  }
}

/** Backend : celui choisi au lancement d'Electron, sinon ?backend=… , sinon le port 8094 du même hôte. */
function backendUrl(): string {
  const fromQuery = new URLSearchParams(location.search).get('backend');
  const host = location.hostname || 'localhost';
  return (window.desktop?.backendUrl ?? fromQuery ?? `http://${host}:8094`).replace(/\/$/, '');
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App apiBase={backendUrl()} plugins={plugins} />
  </StrictMode>,
);
