import type { FieldDescriptor, ProductDto } from './api/client';
import { CORE } from './api/client';

const number = (n: number) => n.toLocaleString('fr-FR');

/** Affichage par défaut d'une valeur, d'après le type et les options du champ. */
export function formatValue(field: FieldDescriptor, value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  switch (field.type) {
    case 'BOOLEAN': return value ? 'Oui' : 'Non';
    case 'NUMBER': return field.name === 'price' && field.namespace === CORE ? `${Number(value).toFixed(2)} €` : number(Number(value));
    case 'CHOICE': return field.options?.find((o) => o.value === value)?.label ?? String(value);
    default: return String(value);
  }
}

/** Texte d'aide décrivant les bornes d'un champ numérique. */
export function boundsHint(field: FieldDescriptor): string {
  const { min, max } = field;
  if (min != null && max != null) return `entre ${number(min)} et ${number(max)}`;
  if (min != null) return `≥ ${number(min)}`;
  if (max != null) return `≤ ${number(max)}`;
  return '';
}

export type Draft = Record<string, unknown>; // clé de champ -> valeur

export function valueOf(product: ProductDto | null, field: FieldDescriptor): unknown {
  if (!product) return null;
  if (field.namespace === CORE) return (product as unknown as Record<string, unknown>)[field.name] ?? null;
  return product.extensions[field.namespace]?.[field.name] ?? null;
}

/** Produit à envoyer à core-backend, reconstruit à partir des valeurs du formulaire. */
export function toProduct(schema: readonly FieldDescriptor[], draft: Draft, id: number | null): ProductDto {
  const product: Record<string, unknown> = { id, extensions: {} };
  const extensions = product.extensions as Record<string, Record<string, unknown>>;
  for (const field of schema) {
    const key = field.namespace === CORE ? field.name : `${field.namespace}.${field.name}`;
    const value = draft[key] ?? null;
    if (field.namespace === CORE) {
      if (value !== null) product[field.name] = value;
    } else {
      (extensions[field.namespace] ??= {})[field.name] = value;
    }
  }
  return product as unknown as ProductDto;
}
