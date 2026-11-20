// Registre d'extensions de l'IHM. Les panneaux du noyau ne consultent que ce registre ;
// les plug-ins le remplissent via leur fonction register(registry).

const editors = new Map();     // clé de champ -> (descripteur, valeur) => { element, value() }
const formatters = new Map();  // clé de champ -> valeur => texte
const sections = new Map();    // slot -> [(produit, ctx) => Element | null]
const titles = new Map();      // namespace -> titre

export const fieldKey = (d) => (d.namespace === 'core' ? d.name : `${d.namespace}.${d.name}`);

export const registry = {
  fieldEditor(key, factory) { editors.set(key, factory); },
  fieldFormatter(key, format) { formatters.set(key, format); },
  section(slot, factory) {
    if (!sections.has(slot)) sections.set(slot, []);
    sections.get(slot).push(factory);
  },
  namespaceTitle(namespace, title) { titles.set(namespace, title); },

  // --- utilisé par les panneaux du noyau ---
  editorFor(d) { return editors.get(fieldKey(d)) ?? defaultEditor; },
  format(d, value) {
    const f = formatters.get(fieldKey(d));
    return f ? f(value) : defaultFormat(d, value);
  },
  sectionsFor(slot) { return sections.get(slot) ?? []; },
  titleOf(namespace) { return titles.get(namespace) ?? namespace; },
};

function defaultFormat(d, value) {
  if (value === null || value === undefined) return '—';
  if (d.type === 'BOOLEAN') return value ? 'Oui' : 'Non';
  if (d.type === 'NUMBER') return Number(value).toLocaleString('fr-FR');
  return String(value);
}

// Éditeur générique, choisi d'après le type déclaré par le backend.
export function defaultEditor(d, value) {
  const input = document.createElement('input');
  input.name = fieldKey(d);
  if (d.type === 'BOOLEAN') {
    input.type = 'checkbox';
    input.checked = value === true;
    const label = document.createElement('label');
    label.className = 'check';
    label.append(input, document.createTextNode(` ${d.label}`));
    return { element: label, value: () => input.checked, inline: true };
  }
  input.type = d.type === 'NUMBER' ? 'number' : 'text';
  if (d.type === 'NUMBER') input.step = 'any';
  input.value = value ?? '';
  return {
    element: input,
    value: () => {
      if (input.value.trim() === '') return null;
      return d.type === 'NUMBER' ? Number(input.value) : input.value;
    },
  };
}
