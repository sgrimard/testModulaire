import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Le rendu est un site statique chargé par Electron (protocole app://desktop) ;
// `npm run dev` le sert aussi dans un navigateur pour le développement.
export default defineConfig({
  plugins: [react()],
  root: 'src/renderer',
  base: './',
  build: { outDir: '../../dist/renderer', emptyOutDir: true },
  // Serveurs de développement uniquement : accessibles depuis un autre conteneur ou poste.
  server: { port: 5173, strictPort: true, allowedHosts: true },
  preview: { port: 5173, strictPort: true, allowedHosts: true },
});
