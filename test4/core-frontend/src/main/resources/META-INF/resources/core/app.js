// IHM du noyau (test 4). Aucune référence à une extension ni à checking :
//  - les champs, y compris ceux des extensions de données, viennent du schéma de core-backend ;
//  - les panneaux supplémentaires (rapport de checking...) viennent des plug-ins UI du registre ;
//  - la liste se met à jour en s'abonnant aux changements de core-backend (SSE).
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

async function loadProducts() { state.products = await http('GET', API); }

async function refresh(selectId = state.selectedId) {
  await loadProducts();
  state.selectedId = selectId;
  renderList();
  renderDetail();
}

const select = (id) => { state.selectedId = id; renderList(); renderDetail(); };

// --- Panneau liste ---------------------------------------------------------------------------
function renderList() {
  const columns = state.schema;
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
  const fields = new Map(); // clé -> { descriptor, editor }

  const fieldBlock = (d) => {
    const editor = registry.editorFor(d)(d, valueOf(product, d));
    fields.set(fieldKey(d), { descriptor: d, editor });
    return el('div', { class: 'field', 'data-field': fieldKey(d) },
      editor.inline ? null : el('label', {}, d.label + (d.required ? ' *' : '')),
      editor.element,
      el('small', { class: 'hint' }, boundsHint(d)),
      el('small', { class: 'error' }));
  };

  const draft = () => {
    const body = { extensions: {} };
    for (const { descriptor: d, editor } of fields.values()) {
      const v = editor.value();
      if (d.namespace === 'core') { if (v !== null) body[d.name] = v; } else (body.extensions[d.namespace] ??= {})[d.name] = v;
    }
    return body;
  };

  const form = el('form', { onsubmit: (e) => { e.preventDefault(); save(); } },
    coreFields().map(fieldBlock),
    namespaces().map((ns) => el('fieldset', { 'data-namespace': ns },
      el('legend', {}, registry.titleOf(ns)),
      extensionFields().filter((d) => d.namespace === ns).map(fieldBlock))),
    el('div', { class: 'buttons' },
      el('button', { type: 'submit', class: 'primary' }, product ? 'Enregistrer' : 'Créer'),
      product ? el('button', { type: 'button', class: 'danger', onclick: remove }, 'Supprimer') : null),
    el('p', { class: 'status', role: 'status' }));

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
    product ? pluginSections(product) : null,
    form,
  ].filter(Boolean));
}

function pluginSections(product) {
  const nodes = registry.sectionsFor('product-detail').map((factory) => {
    try { return factory(product, { api: API }); } catch (e) { console.warn(e); return null; }
  }).filter(Boolean);
  return nodes.length ? el('div', { class: 'plugin-sections' }, nodes) : null;
}

// --- Abonnement aux changements de core-backend -------------------------------------------------
// Seule la liste est redessinée : un formulaire en cours de saisie n'est pas écrasé.
function subscribe() {
  const source = new EventSource(`${API}/events`);
  source.onmessage = async (message) => {
    const event = JSON.parse(message.data);
    if (event.type === 'subscribed') return;
    await loadProducts();
    renderList();
  };
}

// --- Démarrage ----------------------------------------------------------------------------------
(async () => {
  const [schema, extensions] = await Promise.all([http('GET', `${API}/schema`), http('GET', `${API}/extensions`)]);
  state.schema = schema;
  document.querySelector('#extensions').textContent =
    extensions.length ? `Extensions de données : ${extensions.join(', ')}` : 'Aucune extension de données';
  await loadPlugins();
  await refresh(null);
  subscribe();
})();
