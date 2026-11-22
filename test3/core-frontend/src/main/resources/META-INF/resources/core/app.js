// Panneaux du noyau. Aucune référence à un plug-in : champs ET valeurs valides viennent du schéma
// effectif calculé par le backend ; rendus spécialisés et sections viennent du registre.
import { registry, fieldKey, boundsHint } from './registry.js';

const API = '/api/products';
const state = { schema: [], products: [], selectedId: null };

const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined));
  return node;
};

async function http(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 400) throw Object.assign(new Error('invalid'), { violations: (await res.json()).violations });
  if (!res.ok) throw new Error(`${method} ${url} : HTTP ${res.status}`);
  if (res.status === 204) return null;
  return (res.headers.get('content-type') ?? '').includes('json') ? res.json() : res.text();
}

const coreFields = () => state.schema.filter((d) => d.namespace === 'core');
const extensionFields = () => state.schema.filter((d) => d.namespace !== 'core');
const namespaces = () => [...new Set(extensionFields().map((d) => d.namespace))];
const valueOf = (product, d) => (d.namespace === 'core' ? product?.[d.name] : product?.extensions?.[d.namespace]?.[d.name]) ?? null;

// Le noyau utilise le même registre pour ses propres rendus spécialisés.
registry.fieldEditor('description', (d, value) => {
  const area = el('textarea', { name: 'description', rows: '2' });
  area.value = value ?? '';
  return { element: area, value: () => area.value || null };
});

async function loadPlugins() {
  const plugins = await http('GET', '/ui/plugins');
  for (const p of plugins) {
    try {
      (await import(p.module)).register(registry);
    } catch (e) {
      console.warn(`Plug-in UI « ${p.id} » ignoré`, e); // un plug-in défaillant ne casse pas le noyau
    }
  }
  document.querySelector('#plugins').textContent =
    plugins.length ? `Plug-ins UI : ${plugins.map((p) => p.id).join(', ')}` : 'Aucun plug-in UI';
}

async function refresh(selectId = state.selectedId) {
  state.products = await http('GET', API);
  state.selectedId = selectId;
  renderList();
  renderDetail();
}

const select = (id) => { state.selectedId = id; renderList(); renderDetail(); };

// --- Panneau liste ---------------------------------------------------------------------------
function renderList() {
  const columns = state.schema.filter((d) => d.name !== 'description');
  const table = el('table', { class: 'list' },
    el('thead', {}, el('tr', {}, columns.map((d) => el('th', { class: d.type === 'NUMBER' ? 'num' : '' }, d.label)))),
    el('tbody', {}, state.products.map((p) => el('tr', {
      class: p.id === state.selectedId ? 'selected' : '', 'data-id': p.id, onclick: () => select(p.id),
    }, columns.map((d) => el('td', { class: d.type === 'NUMBER' ? 'num' : '' },
      d.key === 'price' ? `${Number(p.price).toFixed(2)} €` : registry.format(d, valueOf(p, d))))))));
  document.querySelector('#list').replaceChildren(
    el('header', {}, el('h2', {}, 'Produits'), el('button', { onclick: () => select(null) }, '+ Nouveau')),
    state.products.length ? table : el('p', { class: 'empty' }, 'Aucun produit.'));
}

// --- Panneau détail ----------------------------------------------------------------------------
function renderDetail() {
  const product = state.products.find((p) => p.id === state.selectedId) ?? null;
  const fields = new Map(); // clé -> { descriptor, editor, label, hint }

  const fieldBlock = (d) => {
    const editor = registry.editorFor(d)(d, valueOf(product, d));
    const label = el('label', {});
    const hint = el('small', { class: 'hint' });
    fields.set(fieldKey(d), { descriptor: d, editor, label, hint });
    return el('div', { class: 'field', 'data-field': fieldKey(d) },
      editor.inline ? null : label, editor.element, hint, el('small', { class: 'error' }));
  };

  // Applique un schéma (éventuellement contextuel) aux champs déjà affichés.
  const applySchema = (schema) => {
    for (const d of schema) {
      const f = fields.get(fieldKey(d));
      if (!f) continue;
      f.descriptor = d;
      f.label.textContent = d.label + (d.required ? ' *' : '');
      f.hint.textContent = boundsHint(d);
      f.editor.update?.(d);
    }
  };

  const draft = () => {
    const body = { extensions: {} };
    for (const { descriptor: d, editor } of fields.values()) {
      const v = editor.value();
      if (d.namespace === 'core') { if (v !== null) body[d.name] = v; } else (body.extensions[d.namespace] ??= {})[d.name] = v;
    }
    return body;
  };

  // Les valeurs valides peuvent dépendre d'autres champs : on redemande le schéma à chaque modification.
  let pending = 0;
  const onChange = async () => {
    const ticket = ++pending;
    const schema = await http('POST', `${API}/schema`, draft());
    if (ticket === pending) applySchema(schema);
  };

  const form = el('form', { onsubmit: (e) => { e.preventDefault(); save(); }, onchange: onChange },
    coreFields().map(fieldBlock),
    namespaces().map((ns) => el('fieldset', { 'data-namespace': ns },
      el('legend', {}, registry.titleOf(ns)),
      extensionFields().filter((d) => d.namespace === ns).map(fieldBlock))),
    el('div', { class: 'buttons' },
      el('button', { type: 'submit', class: 'primary' }, product ? 'Enregistrer' : 'Créer'),
      product ? el('button', { type: 'button', class: 'danger', onclick: remove }, 'Supprimer') : null),
    el('p', { class: 'status', role: 'status' }));
  applySchema(state.schema);
  onChange(); // contraintes contextuelles du produit chargé

  const showViolations = (violations) => {
    form.querySelectorAll('.error').forEach((s) => { s.textContent = ''; });
    form.querySelector('.status').textContent = '';
    for (const v of violations) {
      const target = form.querySelector(`[data-field="${CSS.escape(v.field)}"] .error`);
      if (target) target.textContent = v.message;
      else form.querySelector('.status').textContent = `${v.field} : ${v.message}`;
    }
  };

  async function save() {
    try {
      const body = draft();
      const saved = product ? await http('PUT', `${API}/${product.id}`, body) : await http('POST', API, body);
      await refresh(saved.id);
      document.querySelector('#detail .status').textContent = 'Enregistré.';
    } catch (e) {
      if (e.violations) showViolations(e.violations); else throw e;
    }
  }

  async function remove() {
    await http('DELETE', `${API}/${product.id}`);
    await refresh(null);
  }

  document.querySelector('#detail').replaceChildren(...[
    el('header', {}, el('h2', {}, product ? `Produit #${product.id}` : 'Nouveau produit')),
    form,
    product ? processingPanel(product) : null,
    product ? pluginSections(product) : null,
  ].filter(Boolean));
}

function processingPanel(product) {
  const quantity = el('input', { type: 'number', min: '1', value: '1', name: 'quantity' });
  const result = el('div', { class: 'quote' });
  const label = el('strong', { class: 'label' });
  http('GET', `${API}/${product.id}/label`).then((text) => { label.textContent = text; });

  const compute = async () => {
    const quote = await http('GET', `${API}/${product.id}/quote?quantity=${quantity.value}`);
    result.replaceChildren(el('table', {},
      el('tbody', {}, quote.lines.map((l) => el('tr', { 'data-code': l.code },
        el('td', {}, l.label), el('td', { class: 'num' }, `${Number(l.amount).toFixed(2)} €`)))),
      el('tfoot', {}, el('tr', {}, el('th', {}, 'Total'), el('th', { class: 'num' }, `${Number(quote.total).toFixed(2)} €`)))));
  };

  return el('section', { class: 'card' },
    el('h3', {}, 'Traitements'),
    el('p', { class: 'muted' }, 'Libellé : ', label),
    el('div', { class: 'inline' }, el('label', {}, 'Quantité '), quantity,
      el('button', { type: 'button', onclick: compute }, 'Calculer le devis')),
    result);
}

function pluginSections(product) {
  const nodes = registry.sectionsFor('product-detail').map((factory) => {
    try { return factory(product, { api: API }); } catch (e) { console.warn(e); return null; }
  }).filter(Boolean);
  return nodes.length ? el('div', { class: 'plugin-sections' }, nodes) : null;
}

// --- Démarrage ----------------------------------------------------------------------------------
(async () => {
  state.schema = await http('GET', `${API}/schema`);
  await loadPlugins();
  await refresh(null);
})();
