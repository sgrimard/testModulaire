// Expose au rendu la seule information dont il a besoin : l'URL du backend.
import { contextBridge } from 'electron';

const arg = process.argv.find((a) => a.startsWith('--backend-url='));
contextBridge.exposeInMainWorld('desktop', { backendUrl: arg?.slice('--backend-url='.length) ?? 'http://localhost:8100' });
