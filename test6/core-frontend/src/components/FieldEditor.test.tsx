import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { FieldDescriptor } from '../api/client';
import { DefaultFieldEditor } from './FieldEditor';

const category = {
  namespace: 'core', name: 'category', label: 'Catégorie', type: 'CHOICE', required: true, min: null, max: null,
  options: [{ value: 'tools', label: 'Outillage' }, { value: 'garden', label: 'Jardin' }],
} as FieldDescriptor;

describe('DefaultFieldEditor', () => {
  it('garde visible une valeur enregistrée qui ne fait plus partie des options', () => {
    render(<DefaultFieldEditor field={category} value="chemicals" onChange={() => {}} />);
    const select = screen.getByRole('combobox') as HTMLSelectElement;
    expect(select.value).toBe('chemicals');
    expect(screen.getByRole('option', { name: 'chemicals (non autorisée)' })).toBeTruthy();
  });

  it('convertit la saisie numérique et le vide en null', () => {
    const onChange = vi.fn();
    const price = { ...category, name: 'price', type: 'NUMBER', options: null } as FieldDescriptor;
    render(<DefaultFieldEditor field={price} value={null} onChange={onChange} />);
    const input = screen.getByRole('spinbutton');
    fireEvent.change(input, { target: { value: '12.5' } });
    expect(onChange).toHaveBeenLastCalledWith(12.5);
  });
});
