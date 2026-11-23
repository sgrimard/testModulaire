// Registre d'extensions de l'IHM. Les panneaux du noyau ne consultent que ce registre ;
// les plug-ins (et le noyau lui-même) le remplissent.
//
// Contrat d'un éditeur : factory(descripteur, valeur) => {
//   element,            // nœud DOM à insérer
//   value(),            // valeur courante (typée)
//   update(descripteur) // optionnel : applique de nouvelles contraintes (options, min/max, obligatoire)
//   inline              // optionnel : l'éditeur porte lui-même son libellé
// }

const editors = new Map();     // clé de champ -> factory
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
  if (d.type === 'CHOICE') return d.options?.find((o) => o.value === value)?.label ?? value;
  return String(value);
}

export const formatNumber = (n) => Number(n).toLocaleString('fr-FR');

/** Texte d'aide décrivant les bornes d'un champ numérique. */
export function boundsHint(d) {
  if (d.min != null && d.max != null) return `entre ${formatNumber(d.min)} et ${formatNumber(d.max)}`;
  if (d.min != null) return `≥ ${formatNumber(d.min)}`;
  if (d.max != null) return `≤ ${formatNumber(d.max)}`;
  return '';
}

// Éditeurs génériques, choisis d'après le type et les contraintes déclarés par le backend.
export function defaultEditor(d, value) {
  if (d.type === 'BOOLEAN') return checkboxEditor(d, value);
  if (d.type === 'CHOICE') return selectEditor(d, value);
  return inputEditor(d, value);
}

function checkboxEditor(d, value) {
  const input = Object.assign(document.createElement('input'), { type: 'checkbox', name: fieldKey(d), checked: value === true });
  const label = document.createElement('label');
  label.className = 'check';
  label.append(input, document.createTextNode(` ${d.label}`));
  return { element: label, value: () => input.checked, inline: true };
}

function inputEditor(d, value) {
  const input = Object.assign(document.createElement('input'), { name: fieldKey(d), value: value ?? '' });
  input.type = d.type === 'NUMBER' ? 'number' : 'text';
  if (d.type === 'NUMBER') input.step = 'any';
  const update = (desc) => {
    input.required = desc.required;
    for (const bound of ['min', 'max']) {
      if (desc[bound] != null) input[bound] = desc[bound]; else input.removeAttribute(bound);
    }
  };
  update(d);
  return {
    element: input,
    update,
    value: () => {
      if (input.value.trim() === '') return null;
      return d.type === 'NUMBER' ? Number(input.value) : input.value;
    },
  };
}

function selectEditor(d, value) {
  const select = Object.assign(document.createElement('select'), { name: fieldKey(d) });
  let current = value ?? '';
  const labels = new Map(); // libellés déjà vus, pour nommer une valeur qui sort de la liste
  select.addEventListener('change', () => { current = select.value; });
  const update = (desc) => {
    select.required = desc.required;
    const options = [...(desc.options ?? [])];
    options.forEach((o) => labels.set(o.value, o.label));
    // Une valeur déjà saisie mais devenue interdite reste visible (et signalée) plutôt que perdue.
    if (current && !options.some((o) => o.value === current)) {
      options.push({ value: current, label: `${labels.get(current) ?? current} (non autorisée)`, invalid: true });
    }
    select.replaceChildren(
      Object.assign(document.createElement('option'), { value: '', textContent: desc.required ? '— choisir —' : '—' }),
      ...options.map((o) => Object.assign(document.createElement('option'), {
        value: o.value, textContent: o.label, className: o.invalid ? 'invalid' : '',
      })),
    );
    select.value = current;
  };
  update(d);
  return { element: select, update, value: () => (select.value === '' ? null : select.value) };
}
