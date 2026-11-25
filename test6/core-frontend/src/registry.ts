// Contrat entre l'IHM du noyau et ses plug-ins. Un plug-in N'IMPORTE PAS ce fichier : il décrit
// la forme qu'il attend (typage structurel) ; l'application qui assemble les deux (desktop)
// vérifie à la compilation que les formes concordent.
import type { ReactNode } from 'react';
import type { FieldDescriptor, ProductDto } from './api/client';
import { fieldKey } from './api/client';

/** Éditeur de champ : contrôlé, il reçoit la valeur et signale les changements. */
export interface FieldEditorProps {
  field: FieldDescriptor;
  value: unknown;
  onChange: (value: unknown) => void;
}

/** Bloc ajouté au détail d'un produit enregistré. */
export interface ProductSectionProps {
  product: ProductDto;
  /** URL de base des API REST (même hôte pour toutes les briques de l'assemblage). */
  apiBase: string;
}

/**
 * Composants fournis par les plug-ins : des fonctions (composants React fonctionnels). Ce type,
 * plus simple que ComponentType, laisse TypeScript accepter une section qui attend moins de
 * propriétés que l'hôte n'en fournit, et refuser celle qui en attend une qu'il ne fournit pas.
 */
export type FieldEditor = (props: FieldEditorProps) => ReactNode;
export type ProductSection = (props: ProductSectionProps) => ReactNode;

export interface UiRegistry {
  namespaceTitle: (namespace: string, title: string) => void;
  fieldFormatter: (key: string, format: (value: unknown) => string) => void;
  fieldEditor: (key: string, editor: FieldEditor) => void;
  productSection: (id: string, section: ProductSection) => void;
}

export interface UiPlugin {
  id: string;
  register: (registry: UiRegistry) => void;
}

/** Ce que les composants du noyau lisent ; rempli par les plug-ins au démarrage. */
export interface ResolvedRegistry {
  plugins: string[];
  titleOf: (namespace: string) => string;
  formatterFor: (field: FieldDescriptor) => ((value: unknown) => string) | undefined;
  editorFor: (field: FieldDescriptor) => FieldEditor | undefined;
  sections: Array<{ id: string; component: ProductSection }>;
}

export function buildRegistry(plugins: readonly UiPlugin[]): ResolvedRegistry {
  const titles = new Map<string, string>();
  const formatters = new Map<string, (value: unknown) => string>();
  const editors = new Map<string, FieldEditor>();
  const sections: ResolvedRegistry['sections'] = [];
  const registered: string[] = [];

  const registry: UiRegistry = {
    namespaceTitle: (ns, title) => void titles.set(ns, title),
    fieldFormatter: (key, format) => void formatters.set(key, format),
    fieldEditor: (key, editor) => void editors.set(key, editor),
    productSection: (id, component) => void sections.push({ id, component }),
  };

  for (const plugin of plugins) {
    try {
      plugin.register(registry);
      registered.push(plugin.id);
    } catch (e) {
      console.warn(`Plug-in d'IHM « ${plugin.id} » ignoré`, e); // un plug-in défaillant ne casse pas le noyau
    }
  }

  return {
    plugins: registered,
    titleOf: (ns) => titles.get(ns) ?? ns,
    formatterFor: (f) => formatters.get(fieldKey(f)),
    editorFor: (f) => editors.get(fieldKey(f)),
    sections,
  };
}
