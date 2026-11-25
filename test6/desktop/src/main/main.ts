// Processus principal Electron : une fenêtre qui charge l'IHM assemblée, servie par le protocole
// app://desktop (origine stable, autorisée par le CORS du backend).
import { app, BrowserWindow, net, protocol, shell } from 'electron';
import { join, normalize } from 'node:path';
import { pathToFileURL } from 'node:url';

const DEFAULT_BACKEND = 'http://localhost:8100';

/** Backend : option --backend=URL, sinon variable BACKEND_URL, sinon le port 8100 local. */
function backendUrl(): string {
  const arg = process.argv.find((a) => a.startsWith('--backend='));
  return arg?.slice('--backend='.length) ?? process.env.BACKEND_URL ?? DEFAULT_BACKEND;
}

// Pas de `secure: true` : une page « sécurisée » ne pourrait pas appeler un backend en http://
// sur une autre machine (contenu mixte bloqué par Chromium, sauf pour localhost).
protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, supportFetchAPI: true, corsEnabled: true } },
]);

const rendererDir = join(__dirname, '..', 'renderer');

function serveRenderer() {
  protocol.handle('app', (request) => {
    const path = decodeURIComponent(new URL(request.url).pathname);
    const file = normalize(join(rendererDir, path === '/' ? 'index.html' : path));
    if (!file.startsWith(rendererDir)) return new Response('Interdit', { status: 403 });
    return net.fetch(pathToFileURL(file).toString());
  });
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1280,
    height: 860,
    title: 'Catalogue et contrôles',
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      additionalArguments: [`--backend-url=${backendUrl()}`],
    },
  });
  // Les liens externes s'ouvrent dans le navigateur du système, pas dans l'application.
  window.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url);
    return { action: 'deny' };
  });
  void window.loadURL('app://desktop/index.html');
}

app.whenReady().then(() => {
  serveRenderer();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
