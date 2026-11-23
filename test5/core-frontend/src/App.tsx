import { useCallback, useEffect, useMemo, useState } from 'react';
import { productApi, type FieldDescriptor, type ProductDto } from './api/client';
import { buildRegistry, type UiPlugin } from './registry';
import { ProductList } from './components/ProductList';
import { ProductDetail } from './components/ProductDetail';

export interface AppProps {
  /** URL de base du backend (ex. http://localhost:8094). */
  apiBase: string;
  /** Plug-ins d'IHM de l'assemblage. */
  plugins: readonly UiPlugin[];
}

/**
 * IHM du noyau. Elle ne connaît ni les extensions de données (leurs champs viennent du schéma)
 * ni les plug-ins (leurs sections viennent du registre).
 */
export function App({ apiBase, plugins }: AppProps) {
  const api = useMemo(() => productApi(apiBase), [apiBase]);
  const registry = useMemo(() => buildRegistry(plugins), [plugins]);
  const [schema, setSchema] = useState<FieldDescriptor[]>([]);
  const [extensions, setExtensions] = useState<string[]>([]);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0); // remonte le formulaire après enregistrement

  const reload = useCallback(() => api.list().then(setProducts), [api]);

  useEffect(() => {
    Promise.all([api.schema(), api.extensions(), api.list()])
      .then(([s, e, p]) => { setSchema(s); setExtensions(e); setProducts(p); })
      .catch((e: unknown) => setError(`Backend injoignable (${apiBase}) : ${String(e)}`));
    // Changements faits ailleurs : seule la liste est rechargée, la saisie en cours est préservée.
    return api.subscribe((event) => { if (event.type !== 'subscribed') void reload(); });
  }, [api, apiBase, reload]);

  const select = (id: number | null) => { setSelectedId(id); setFormKey((k) => k + 1); };
  const product = products.find((p) => p.id === selectedId) ?? null;

  return (
    <>
      <header className="top">
        <h1>Catalogue produits</h1>
        <span className="muted">{extensions.length ? `Extensions de données : ${extensions.join(', ')}` : 'Aucune extension de données'}</span>
        <span className="muted">{registry.plugins.length ? `Plug-ins UI : ${registry.plugins.join(', ')}` : 'Aucun plug-in UI'}</span>
      </header>
      {error ? <p className="fatal" role="alert">{error}</p> : (
        <main>
          <ProductList schema={schema} products={products} selectedId={selectedId} registry={registry} onSelect={select} />
          {schema.length > 0 && (
            <ProductDetail key={`${selectedId}-${formKey}`} api={api} schema={schema} product={product} registry={registry}
              onSaved={(id) => { void reload().then(() => select(id)); }}
              onDeleted={() => { void reload().then(() => select(null)); }} />
          )}
        </main>
      )}
    </>
  );
}
