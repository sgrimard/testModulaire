import { useState, type FormEvent } from 'react';
import { CORE, ValidationError, fieldKey, type FieldDescriptor, type ProductApi, type ProductDto } from '../api/client';
import { boundsHint, toProduct, valueOf, type Draft } from '../format';
import type { ResolvedRegistry } from '../registry';
import { DefaultFieldEditor, isInline } from './FieldEditor';

interface Props {
  api: ProductApi;
  schema: readonly FieldDescriptor[];
  product: ProductDto | null;
  registry: ResolvedRegistry;
  onSaved: (id: number) => void;
  onDeleted: () => void;
}

const initialDraft = (schema: readonly FieldDescriptor[], product: ProductDto | null): Draft =>
  Object.fromEntries(schema.map((f) => [fieldKey(f), valueOf(product, f)]));

/** Détail d'un produit : sections des plug-ins, puis formulaire piloté par le schéma. */
export function ProductDetail({ api, schema, product, registry, onSaved, onDeleted }: Props) {
  const [draft, setDraft] = useState<Draft>(() => initialDraft(schema, product));
  const [violations, setViolations] = useState<Record<string, string>>({});
  const [status, setStatus] = useState('');
  const id = product?.id ?? null;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setStatus('');
    try {
      const body = toProduct(schema, draft, id);
      const saved = id === null ? await api.create(body) : await api.update(id, body);
      setViolations({});
      setStatus('Enregistré.');
      onSaved(saved.id!);
    } catch (err) {
      if (!(err instanceof ValidationError)) throw err;
      setViolations(Object.fromEntries(err.violations.map((v) => [v.field, v.message])));
      const orphan = err.violations.find((v) => !schema.some((f) => fieldKey(f) === v.field));
      if (orphan) setStatus(`${orphan.field} : ${orphan.message}`);
    }
  };

  const remove = async () => {
    if (id === null) return;
    await api.remove(id);
    onDeleted();
  };

  const field = (f: FieldDescriptor) => {
    const key = fieldKey(f);
    const Editor = registry.editorFor(f) ?? DefaultFieldEditor;
    return (
      <div className="field" data-field={key} key={key}>
        {!isInline(f.type) && <label htmlFor={key}>{f.label}{f.required ? ' *' : ''}</label>}
        <Editor field={f} value={draft[key]} onChange={(value) => setDraft((d) => ({ ...d, [key]: value }))} />
        {boundsHint(f) && <small className="hint">{boundsHint(f)}</small>}
        {violations[key] && <small className="error">{violations[key]}</small>}
      </div>
    );
  };

  const namespaces = [...new Set(schema.filter((f) => f.namespace !== CORE).map((f) => f.namespace))];

  return (
    <section className="panel" aria-label="Détail du produit">
      <header><h2>{id === null ? 'Nouveau produit' : `Produit #${id}`}</h2></header>
      {product && id !== null && registry.sections.length > 0 && (
        <div className="plugin-sections">
          {registry.sections.map(({ id: sectionId, component: Section }) => <Section key={sectionId} product={product} apiBase={api.baseUrl} />)}
        </div>
      )}
      <form onSubmit={save}>
        {schema.filter((f) => f.namespace === CORE).map(field)}
        {namespaces.map((ns) => (
          <fieldset key={ns} data-namespace={ns}>
            <legend>{registry.titleOf(ns)}</legend>
            {schema.filter((f) => f.namespace === ns).map(field)}
          </fieldset>
        ))}
        <div className="buttons">
          <button type="submit" className="primary">{id === null ? 'Créer' : 'Enregistrer'}</button>
          {id !== null && <button type="button" className="danger" onClick={remove}>Supprimer</button>}
        </div>
        <p className="status" role="status">{status}</p>
      </form>
    </section>
  );
}
