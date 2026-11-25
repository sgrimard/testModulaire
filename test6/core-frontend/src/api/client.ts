// Client typé de l'API REST de core-backend (seul backend dont dépend cette IHM).
import createClient from 'openapi-fetch';
import type { components, paths } from './schema';

export type ProductDto = components['schemas']['ProductDto'];
export type FieldDescriptor = components['schemas']['FieldDescriptor'];
export type FieldOption = components['schemas']['FieldOption'];
export type ProductEvent = components['schemas']['ProductEvent'];
export type Violation = components['schemas']['Violation'];
export type ApiError = components['schemas']['ApiError'];

export const CORE = 'core';

/** Clé d'un champ : "name" pour le noyau, "<namespace>.<champ>" pour une extension. */
export const fieldKey = (f: Pick<FieldDescriptor, 'namespace' | 'name'>) =>
  f.namespace === CORE ? f.name : `${f.namespace}.${f.name}`;

export class ValidationError extends Error {
  constructor(readonly violations: Violation[]) {
    super('Produit invalide');
  }
}

export type ProductApi = ReturnType<typeof productApi>;

export function productApi(baseUrl: string) {
  const http = createClient<paths>({ baseUrl });

  const unwrap = async <R extends { data?: unknown; error?: unknown; response: Response }>(
    call: Promise<R>,
  ): Promise<NonNullable<R['data']>> => {
    const { data, error, response } = await call;
    if (response.status === 400) throw new ValidationError((error as ApiError).violations ?? []);
    if (!response.ok) throw new Error(`HTTP ${response.status} sur ${response.url}`);
    return data as NonNullable<R['data']>;
  };

  return {
    baseUrl,
    schema: () => unwrap(http.GET('/api/products/schema')),
    extensions: () => unwrap(http.GET('/api/products/extensions')),
    list: () => unwrap(http.GET('/api/products')),
    create: (body: ProductDto) => unwrap(http.POST('/api/products', { body })),
    update: (id: number, body: ProductDto) => unwrap(http.PUT('/api/products/{id}', { params: { path: { id } }, body })),
    remove: (id: number) => unwrap(http.DELETE('/api/products/{id}', { params: { path: { id } } })),
    /** Changements validés (SSE). Renvoie la fonction de désabonnement. */
    subscribe(onEvent: (event: ProductEvent) => void): () => void {
      if (typeof EventSource === 'undefined') return () => {};
      const source = new EventSource(`${baseUrl}/api/products/events`);
      source.onmessage = (m) => onEvent(JSON.parse(m.data) as ProductEvent);
      return () => source.close();
    },
  };
}
