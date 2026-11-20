// Panneaux du noyau. Aucune référence à un plug-in : les champs viennent du schéma backend,
// les rendus spécialisés et sections supplémentaires viennent du registre.
import { registry, fieldKey } from './registry.js';

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
  const type = res.headers.get('content-type') ?? '';
  if (res.status === 204) return null;
  return type.includes('json') ? res.json() : res.text();
}

const extensionFields = () => state.schema.filter((d) => d.namespace !== 'core');
const namespaces = () => [...new Set(extensionFields().map((d) => d.namespace))];
const extValue = (product, d) => product?.extensions?.[d.namespace]?.[d.name] ?? null;

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

// --- Panneau liste ---------------------------------------------------------------------------
function renderList() {
  const ext = extensionFields();
  const table = el('table', { class: 'list' },
    el('thead', {}, el('tr', {},
      el('th', {}, 'Nom'), el('th', { class: 'num' }, 'Prix'),
      ext.map((d) => el('th', {}, d.label)))),
    el('tbody', {}, state.products.map((p) => el('tr', {
      class: p.id === state.selectedId ? 'selected' : '',
      'data-id': p.id,
      onclick: () => { state.selectedId = p.id; renderList(); renderDetail(); },
    },
      el('td', {}, p.name),
      el('td', { class: 'num' }, `${Number(p.price).toFixed(2)} €`),
      ext.map((d) => el('td', {}, registry.format(d, extValue(p, d))))))));
  const panel = document.querySelector('#list');
  panel.replaceChildren(
    el('header', {}, el('h2', {}, 'Produits'),
      el('button', { onclick: () => { state.selectedId = null; renderList(); renderDetail(); } }, '+ Nouveau')),
    state.products.length ? table : el('p', { class: 'empty' }, 'Aucun produit.'));
}

// --- Panneau détail (formulaire + traitements + sections des plug-ins) -------------------------
function renderDetail() {
  const product = state.products.find((p) => p.id === state.selectedId) ?? null;
  const editors = [];

  const field = (key, label, input) =>
    el('div', { class: 'field', 'data-field': key }, el('label', {}, label), input, el('small', { class: 'error' }));

  const name = el('input', { name: 'name', value: product?.name ?? '' });
  const description = el('textarea', { name: 'description', rows: '2' });
  description.value = product?.description ?? '';
  const price = el('input', { name: 'price', type: 'number', step: '0.01', value: product?.price ?? '' });

  const extFieldsets = namespaces().map((ns) => el('fieldset', { 'data-namespace': ns },
    el('legend', {}, registry.titleOf(ns)),
    extensionFields().filter((d) => d.namespace === ns).map((d) => {
      const editor = registry.editorFor(d)(d, extValue(product, d));
      editors.push([d, editor]);
      return editor.inline
        ? el('div', { class: 'field', 'data-field': fieldKey(d) }, editor.element, el('small', { class: 'error' }))
        : field(fieldKey(d), d.label, editor.element);
    })));

  const form = el('form', { onsubmit: (e) => { e.preventDefault(); save(); } },
    field('name', 'Nom', name),
    field('description', 'Description', description),
    field('price', 'Prix unitaire (€)', price),
    extFieldsets,
    el('div', { class: 'buttons' },
      el('button', { type: 'submit', class: 'primary' }, product ? 'Enregistrer' : 'Créer'),
      product ? el('button', { type: 'button', class: 'danger', onclick: remove }, 'Supprimer') : null),
    el('p', { class: 'status', role: 'status' }));

  const showViolations = (violations) => {
    form.querySelectorAll('.error').forEach((s) => { s.textContent = ''; });
    for (const v of violations) {
      const target = form.querySelector(`[data-field="${CSS.escape(v.field)}"] .error`);
      if (target) target.textContent = v.message;
      else form.querySelector('.status').textContent = `${v.field} : ${v.message}`;
    }
  };

  async function save() {
    const extensions = {};
    for (const [d, editor] of editors) (extensions[d.namespace] ??= {})[d.name] = editor.value();
    const body = {
      name: name.value,
      description: description.value || null,
      price: price.value === '' ? 0 : Number(price.value),
      extensions,
    };
    try {
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

  const panel = document.querySelector('#detail');
  panel.replaceChildren(...[
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
