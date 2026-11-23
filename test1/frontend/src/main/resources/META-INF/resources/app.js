// IHM générique du test 1 : comme le client Kotlin, elle découvre les extensions par leur nom
// (GET /extensions), puis les utilise par get / put et s'abonne à leurs événements (SSE).
// Elle ne contient le nom d'aucune extension.

const API = '/extensions';

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

const valueUrl = (name, key) => `${API}/${encodeURIComponent(name)}/values/${encodeURIComponent(key)}`;

function extensionCard(name) {
  const key = el('input', { name: 'key', placeholder: 'clé', required: '' });
  const value = el('input', { name: 'value', placeholder: 'valeur' });
  const result = el('p', { class: 'result', role: 'status' });
  const log = el('ul', { class: 'log', 'aria-label': `Événements de ${name}` });

  const show = (text, kind = '') => { result.className = `result ${kind}`; result.textContent = text; };

  async function read() {
    if (!key.reportValidity()) return;
    const res = await fetch(valueUrl(name, key.value));
    if (res.status === 404) show(`Aucune valeur pour « ${key.value} ».`, 'missing');
    else if (res.ok) { const text = await res.text(); value.value = text; show(`${key.value} = ${text}`); }
    else show(`Erreur HTTP ${res.status}`, 'error');
  }

  async function write() {
    if (!key.reportValidity()) return;
    const res = await fetch(valueUrl(name, key.value), {
      method: 'PUT', headers: { 'Content-Type': 'text/plain' }, body: value.value,
    });
    show(res.ok ? `Écrit : ${key.value} = ${value.value}` : `Refusé (HTTP ${res.status}) : ${await res.text()}`, res.ok ? '' : 'error');
  }

  // Abonnement : chaque événement publié par l'extension s'ajoute en tête du journal.
  const source = new EventSource(`${API}/${encodeURIComponent(name)}/events`);
  source.onmessage = (message) => {
    const event = JSON.parse(message.data);
    const detail = [event.key, event.value].filter((v) => v != null).join(' = ');
    log.prepend(el('li', { class: event.type },
      el('time', {}, new Date().toLocaleTimeString('fr-FR')),
      el('span', { class: 'type' }, event.type),
      el('span', {}, detail)));
    while (log.children.length > 50) log.lastChild.remove();
  };

  return el('article', { class: 'panel', 'data-extension': name },
    el('header', {}, el('h2', {}, name)),
    el('form', { class: 'kv', onsubmit: (e) => { e.preventDefault(); write(); } },
      key, value,
      el('div', { class: 'buttons' },
        el('button', { type: 'button', onclick: read }, 'Lire'),
        el('button', { type: 'submit', class: 'primary' }, 'Écrire'))),
    result,
    el('h3', {}, 'Événements'),
    log);
}

(async () => {
  const names = await (await fetch(API)).json();
  document.querySelector('#summary').textContent = names.length
    ? `${names.length} extension(s) découverte(s) : ${names.join(', ')}`
    : 'Aucune extension dans cet assemblage';
  document.querySelector('#extensions').replaceChildren(
    ...(names.length ? names.map(extensionCard) : [el('p', { class: 'empty' }, 'Aucune extension.')]));
})();
