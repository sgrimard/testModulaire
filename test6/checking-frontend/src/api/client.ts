// Client typé de l'API REST de checking (seul backend dont dépend cette IHM).
import createClient from 'openapi-fetch';
import type { components, paths } from './schema';

export type CheckReport = components['schemas']['CheckReport'];
export type CheckEvent = components['schemas']['CheckEvent'];
export type Metric = components['schemas']['Metric'];
export type Finding = components['schemas']['Finding'];

export function checkingApi(baseUrl: string) {
  const http = createClient<paths>({ baseUrl });
  return {
    async report(productId: number): Promise<CheckReport> {
      const { data, response } = await http.GET('/api/checking/reports/{productId}', { params: { path: { productId } } });
      if (!response.ok || !data) throw new Error(`HTTP ${response.status}`);
      return data;
    },
  };
}

// Un seul abonnement SSE par backend, partagé par toutes les cartes affichées.
const sources = new Map<string, { source: EventSource; listeners: Set<(e: CheckEvent) => void> }>();

/** S'abonne aux rapports recalculés. Renvoie la fonction de désabonnement. */
export function onCheckEvent(baseUrl: string, listener: (event: CheckEvent) => void): () => void {
  if (typeof EventSource === 'undefined') return () => {};
  let entry = sources.get(baseUrl);
  if (!entry) {
    const created = { source: new EventSource(`${baseUrl}/api/checking/events`), listeners: new Set<(e: CheckEvent) => void>() };
    created.source.onmessage = (m) => {
      const event = JSON.parse(m.data) as CheckEvent;
      created.listeners.forEach((l) => l(event));
    };
    sources.set(baseUrl, created);
    entry = created;
  }
  const current = entry;
  current.listeners.add(listener);
  return () => {
    current.listeners.delete(listener);
    if (current.listeners.size === 0) { current.source.close(); sources.delete(baseUrl); }
  };
}
