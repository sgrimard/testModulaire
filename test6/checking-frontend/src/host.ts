// Ce que ce plug-in attend de l'IHM hôte. Volontairement déclaré ICI et non importé de
// core-frontend : la brique ne dépend que de son backend. TypeScript vérifie la concordance
// (typage structurel) là où les deux sont assemblés, dans l'application desktop.
import type { ReactNode } from 'react';

export interface HostSectionProps {
  /** Le produit affiché ; seul son identifiant est utilisé. */
  product: { id: number | null };
  /** URL de base des API REST de l'assemblage. */
  apiBase: string;
}

export interface HostRegistry {
  productSection: (id: string, section: (props: HostSectionProps) => ReactNode) => void;
}
