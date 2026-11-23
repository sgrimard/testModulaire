import { describe, expect, it } from 'vitest';
import type { FieldDescriptor } from './api/client';
import { boundsHint, formatValue, toProduct } from './format';

const field = (f: Partial<FieldDescriptor> & Pick<FieldDescriptor, 'namespace' | 'name' | 'type'>): FieldDescriptor =>
  ({ label: f.name, required: false, options: null, min: null, max: null, ...f }) as FieldDescriptor;

const schema = [
  field({ namespace: 'core', name: 'name', type: 'TEXT' }),
  field({ namespace: 'core', name: 'price', type: 'NUMBER', min: 0, max: 100000 }),
  field({ namespace: 'core', name: 'category', type: 'CHOICE', options: [{ value: 'tools', label: 'Outillage' }] }),
  field({ namespace: 'shipping', name: 'weightKg', type: 'NUMBER' }),
  field({ namespace: 'shipping', name: 'hazardous', type: 'BOOLEAN' }),
];

describe('toProduct', () => {
  it('range les champs du noyau à plat et ceux des extensions par namespace', () => {
    const product = toProduct(schema, { name: 'Pelle', price: 12.5, category: 'tools', 'shipping.weightKg': 2, 'shipping.hazardous': true }, 7);
    expect(product).toEqual({
      id: 7, name: 'Pelle', price: 12.5, category: 'tools',
      extensions: { shipping: { weightKg: 2, hazardous: true } },
    });
  });

  it("omet un champ vide du noyau mais garde null pour une extension (effacement)", () => {
    const product = toProduct(schema, { name: 'x' }, null);
    expect(product).not.toHaveProperty('price');
    expect(product.extensions).toEqual({ shipping: { weightKg: null, hazardous: null } });
  });
});

describe('formatValue', () => {
  it('affiche le libellé des options, le prix en euros et les booléens en français', () => {
    expect(formatValue(schema[2]!, 'tools')).toBe('Outillage');
    expect(formatValue(schema[1]!, 12.5)).toBe('12.50 €');
    expect(formatValue(schema[4]!, false)).toBe('Non');
    expect(formatValue(schema[3]!, null)).toBe('—');
  });
});

describe('boundsHint', () => {
  it('décrit les bornes', () => {
    expect(boundsHint(schema[1]!)).toMatch(/^entre 0 et 100.000$/);
    expect(boundsHint(schema[0]!)).toBe('');
  });
});
