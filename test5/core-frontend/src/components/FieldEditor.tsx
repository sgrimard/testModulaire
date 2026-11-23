// Éditeurs génériques, choisis d'après le type et les contraintes déclarés par core-backend.
import type { FieldEditorProps } from '../registry';
import { fieldKey } from '../api/client';

export function DefaultFieldEditor(props: FieldEditorProps) {
  switch (props.field.type) {
    case 'BOOLEAN': return <CheckboxEditor {...props} />;
    case 'CHOICE': return <SelectEditor {...props} />;
    default: return <InputEditor {...props} />;
  }
}

function InputEditor({ field, value, onChange }: FieldEditorProps) {
  const numeric = field.type === 'NUMBER';
  return (
    <input
      id={fieldKey(field)}
      name={fieldKey(field)}
      type={numeric ? 'number' : 'text'}
      step={numeric ? 'any' : undefined}
      min={field.min ?? undefined}
      max={field.max ?? undefined}
      required={field.required}
      value={value === null || value === undefined ? '' : String(value)}
      onChange={(e) => {
        const raw = e.target.value;
        onChange(raw.trim() === '' ? null : numeric ? Number(raw) : raw);
      }}
    />
  );
}

function CheckboxEditor({ field, value, onChange }: FieldEditorProps) {
  return (
    <label className="check">
      <input type="checkbox" name={fieldKey(field)} checked={value === true} onChange={(e) => onChange(e.target.checked)} />
      {field.label}
    </label>
  );
}

/** Une valeur enregistrée qui n'est plus autorisée reste visible et signalée, au lieu d'être perdue. */
function SelectEditor({ field, value, onChange }: FieldEditorProps) {
  const options = field.options ?? [];
  const current = typeof value === 'string' ? value : '';
  const unknown = current !== '' && !options.some((o) => o.value === current);
  return (
    <select id={fieldKey(field)} name={fieldKey(field)} required={field.required} value={current}
      onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}>
      <option value="">{field.required ? '— choisir —' : '—'}</option>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      {unknown && <option value={current} className="invalid">{current} (non autorisée)</option>}
    </select>
  );
}

/** Les cases à cocher portent elles-mêmes leur libellé. */
export const isInline = (type: string) => type === 'BOOLEAN';
