import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { CheckReport } from './api/client';
import { CheckReportView } from './CheckReportView';

const report: CheckReport = {
  productId: 2, product: 'Cisaille', score: 55, status: 'BLOCKING',
  metrics: [
    { code: 'landed.cost', label: 'Coût de revient rendu', value: 21, unit: '€' },
    { code: 'supplier.grossMarginRate', label: 'Taux de marge brute', value: 10, unit: '%' },
  ],
  findings: [
    { rule: 'supplier.low-margin', severity: 'WARNING', message: 'Marge brute faible (10.0 %)', penalty: 15 },
    { rule: 'landed.margin-absorbed', severity: 'BLOCKING', message: 'Marge absorbée par le transport', penalty: 40 },
  ],
  contributors: ['core', 'shipping', 'supplier', 'landed'],
};

describe('CheckReportView', () => {
  it("affiche le rapport tel qu'il arrive, sans connaître les contributions", () => {
    const { container } = render(<CheckReportView report={report} />);
    expect(screen.getByText('Bloquant', { selector: '.check-status' })).toBeTruthy();
    expect(screen.getByText('55')).toBeTruthy();
    expect(container.querySelector('[data-metric="landed.cost"] .num')?.textContent).toBe('21,00 €');
    expect([...container.querySelectorAll('[data-rule]')].map((li) => li.getAttribute('data-rule')))
      .toEqual(['supplier.low-margin', 'landed.margin-absorbed']);
    expect(screen.getByText(/Calcul : core, shipping, supplier, landed/)).toBeTruthy();
  });
});
