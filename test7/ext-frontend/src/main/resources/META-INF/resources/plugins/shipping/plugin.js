// Plug-in UI « transport » : ne fait que remplir le registre de core-frontend.
// Il est tolérant à l'absence d'ext-backend (aucune donnée « shipping » => rien à afficher).

const STYLE = `
  .shipping-card { border: 1px solid var(--line); border-radius: 8px; padding: .75rem; }
  .shipping-card.hazard { background: var(--warn-bg); border-color: var(--warn); }
  .shipping-card .warn { color: var(--warn); font-weight: 600; }
  .hazard-toggle { display: flex; gap: .5rem; align-items: flex-start; font-size: .9rem; }
  .hazard-toggle small { display: block; color: var(--muted); }
`;

export function register(registry) {
  document.head.append(Object.assign(document.createElement('style'), { textContent: STYLE }));

  registry.namespaceTitle('shipping', 'Transport');

  registry.fieldFormatter('shipping.weightKg', (v) => (v == null ? '—' : `${Number(v).toLocaleString('fr-FR')} kg`));
  registry.fieldFormatter('shipping.hazardous', (v) => (v ? '⚠ dangereux' : '—'));

  // Éditeur spécialisé : case à cocher avec explication de la règle métier.
  registry.fieldEditor('shipping.hazardous', (d, value) => {
    const input = Object.assign(document.createElement('input'), { type: 'checkbox', name: 'shipping.hazardous', checked: value === true });
    const label = document.createElement('label');
    label.className = 'hazard-toggle';
    const text = document.createElement('span');
    text.innerHTML = '⚠ Marchandise dangereuse (ADR)<small>Le poids devient obligatoire ; surtaxe de 20 € au devis.</small>';
    label.append(input, text);
    return { element: label, value: () => input.checked, inline: true };
  });

  // Section ajoutée au panneau détail.
  registry.section('product-detail', (product) => {
    const shipping = product.extensions?.shipping;
    if (!shipping) return null;
    const card = document.createElement('section');
    card.className = `shipping-card${shipping.hazardous ? ' hazard' : ''}`;
    card.dataset.plugin = 'shipping';
    const title = document.createElement('h3');
    title.textContent = 'Fiche transport';
    const weight = document.createElement('p');
    weight.textContent = shipping.weightKg == null ? 'Poids non renseigné.' : `Poids unitaire : ${shipping.weightKg} kg`;
    card.append(title, weight);
    if (shipping.hazardous) {
      const warn = document.createElement('p');
      warn.className = 'warn';
      warn.textContent = '⚠ Expédition soumise à la réglementation ADR.';
      card.append(warn);
    }
    return card;
  });
}
