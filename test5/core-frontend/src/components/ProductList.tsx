import type { FieldDescriptor, ProductDto } from '../api/client';
import { formatValue, valueOf } from '../format';
import type { ResolvedRegistry } from '../registry';

interface Props {
  schema: readonly FieldDescriptor[];
  products: readonly ProductDto[];
  selectedId: number | null;
  registry: ResolvedRegistry;
  onSelect: (id: number | null) => void;
}

export function ProductList({ schema, products, selectedId, registry, onSelect }: Props) {
  return (
    <section className="panel" aria-label="Produits">
      <header>
        <h2>Produits</h2>
        <button type="button" onClick={() => onSelect(null)}>+ Nouveau</button>
      </header>
      {products.length === 0 ? <p className="empty">Aucun produit.</p> : (
        <table className="list">
          <thead>
            <tr>{schema.map((f) => <th key={`${f.namespace}.${f.name}`} className={f.type === 'NUMBER' ? 'num' : ''}>{f.label}</th>)}</tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id ?? 0} data-id={p.id ?? undefined} className={p.id === selectedId ? 'selected' : ''} onClick={() => onSelect(p.id ?? null)}>
                {schema.map((f) => {
                  const value = valueOf(p, f);
                  const format = registry.formatterFor(f);
                  return <td key={`${f.namespace}.${f.name}`} className={f.type === 'NUMBER' ? 'num' : ''}>{format ? format(value) : formatValue(f, value)}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
