// Plug-in UI de checking : ajoute le rapport de contrôle au détail d'un produit.
// Le rendu est piloté par les données du rapport : il ne connaît aucun module ext-checking-*.
// Le rapport affiché se met à jour seul : le plug-in s'abonne aux rapports recalculés (SSE).

const API = '/api/checking';

const STYLE = `
  .check-card { border: 1px solid var(--line); border-radius: 8px; padding: .75rem; }
  .check-card header { display: flex; align-items: baseline; gap: .6rem; flex-wrap: wrap; margin-bottom: .5rem; }
  .check-card h3 { margin: 0; }
  .check-status { font-size: .75rem; font-weight: 600; padding: .1rem .45rem; border-radius: 999px; border: 1px solid currentColor; }
  .check-status.OK { color: #2e7d4f; }
  .check-status.WARNING { color: var(--warn); }
  .check-status.BLOCKING { color: var(--danger); }
  .check-card.BLOCKING { border-color: var(--danger); }
  .check-score { font-size: 1.4rem; font-weight: 600; font-variant-numeric: tabular-nums; }
  .check-score small { font-size: .8rem; color: var(--muted); font-weight: 400; }
  .check-card table { margin-bottom: .5rem; }
  .check-findings { list-style: none; margin: 0 0 .5rem; padding: 0; font-size: .88rem; }
  .check-findings li { display: flex; gap: .5rem; padding: .25rem 0; }
  .check-findings .sev { font-size: .72rem; font-weight: 600; min-width: 5.5rem; }
  .check-findings .sev.WARNING { color: var(--warn); }
  .check-findings .sev.BLOCKING { color: var(--danger); }
  .check-findings .penalty { margin-left: auto; color: var(--muted); font-variant-numeric: tabular-nums; }
  .check-meta { font-size: .78rem; color: var(--muted); margin: 0; }
  .check-card.flash { animation: check-flash 1.2s ease-out; }
  @keyframes check-flash { from { background: var(--accent-soft); } to { background: transparent; } }
`;

const STATUS = { OK: 'Conforme', WARNING: 'À surveiller', BLOCKING: 'Bloquant' };
const SEVERITY = { INFO: 'Info', WARNING: 'Avertissement', BLOCKING: 'Bloquant' };

const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v; else node.setAttribute(k, v);
  }
  node.append(...children.flat().filter((c) => c !== null && c !== undefined));
  return node;
};

const formatValue = (m) => `${Number(m.value).toLocaleString('fr-FR', { minimumFractionDigits: m.unit === '€' ? 2 : 0, maximumFractionDigits: 2 })} ${m.unit}`;

function renderReport(card, report, pushed) {
  card.className = `check-card ${report.status}${pushed ? ' flash' : ''}`;
  card.replaceChildren(
    el('header', {},
      el('h3', {}, 'Contrôle'),
      el('span', { class: `check-status ${report.status}` }, STATUS[report.status] ?? report.status),
      el('span', { class: 'check-score' }, String(report.score), el('small', {}, ' / 100'))),
    report.metrics.length ? el('table', {}, el('tbody', {}, report.metrics.map((m) =>
      el('tr', { 'data-metric': m.code }, el('td', {}, m.label), el('td', { class: 'num' }, formatValue(m)))))) : null,
    report.findings.length
      ? el('ul', { class: 'check-findings' }, report.findings.map((f) => el('li', { 'data-rule': f.rule },
        el('span', { class: `sev ${f.severity}` }, SEVERITY[f.severity] ?? f.severity),
        el('span', {}, f.message),
        f.penalty ? el('span', { class: 'penalty' }, `−${f.penalty}`) : null)))
      : el('p', { class: 'muted' }, 'Aucun problème détecté.'),
    el('p', { class: 'check-meta' },
      `Calcul : ${report.contributors.join(', ')}`,
      pushed ? ` · recalculé à ${new Date().toLocaleTimeString('fr-FR')}` : ''));
}

// Un seul abonnement pour toute la page ; chaque carte affichée s'y inscrit pour son produit.
const watchers = new Map(); // productId -> (event) => void
function subscribe() {
  const source = new EventSource(`${API}/events`);
  source.onmessage = (message) => {
    const event = JSON.parse(message.data);
    const watch = watchers.get(event.productId);
    if (watch) watch(event);
  };
}

export function register(registry) {
  document.head.append(Object.assign(document.createElement('style'), { textContent: STYLE }));
  subscribe();

  registry.section('product-detail', (product) => {
    const card = el('section', { class: 'check-card', 'data-plugin': 'checking' }, el('p', { class: 'muted' }, 'Calcul en cours…'));
    fetch(`${API}/reports/${product.id}`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((report) => renderReport(card, report, false))
      .catch((e) => card.replaceChildren(el('p', { class: 'muted' }, `Rapport indisponible (${e.message}).`)));
    watchers.set(product.id, (event) => {
      if (!card.isConnected) return; // carte remplacée : la suivante a pris la place
      if (event.type === 'checked') renderReport(card, event.report, true);
      if (event.type === 'removed') card.replaceChildren(el('p', { class: 'muted' }, 'Produit supprimé.'));
    });
    return card;
  });
}
