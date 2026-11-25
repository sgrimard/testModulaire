import { CheckReportCard } from './CheckReportCard';
import type { HostRegistry } from './host';

/**
 * Plug-in d'IHM de checking, à passer à l'IHM du noyau par l'assemblage.
 * `register` est une propriété fléchée (et non une méthode) pour que TypeScript compare
 * strictement son paramètre au registre de l'hôte.
 */
export const checkingPlugin = {
  id: 'checking',
  register: (registry: HostRegistry): void => {
    registry.productSection('checking', CheckReportCard);
  },
};

export { CheckReportView } from './CheckReportView';
export type { CheckReport } from './api/client';
