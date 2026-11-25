import { useEffect, useMemo, useState } from 'react';
import { checkingApi, onCheckEvent, type CheckReport } from './api/client';
import { CheckReportView } from './CheckReportView';
import type { HostSectionProps } from './host';

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'removed' }
  | { kind: 'ready'; report: CheckReport; pushedAt?: Date };

/** Section « Contrôle » du détail produit : rapport chargé, puis remplacé à chaque recalcul poussé. */
export function CheckReportCard({ product, apiBase }: HostSectionProps) {
  const api = useMemo(() => checkingApi(apiBase), [apiBase]);
  const [state, setState] = useState<State>({ kind: 'loading' });
  const id = product.id;

  useEffect(() => {
    if (id === null) return;
    let active = true;
    api.report(id)
      .then((report) => { if (active) setState((s) => (s.kind === 'ready' && s.pushedAt ? s : { kind: 'ready', report })); })
      .catch((e: unknown) => { if (active) setState({ kind: 'error', message: String(e) }); });
    const unsubscribe = onCheckEvent(apiBase, (event) => {
      if (event.productId !== id) return;
      if (event.type === 'checked' && event.report) setState({ kind: 'ready', report: event.report, pushedAt: new Date() });
      if (event.type === 'removed') setState({ kind: 'removed' });
    });
    return () => { active = false; unsubscribe(); };
  }, [api, apiBase, id]);

  if (id === null) return null;
  const className = `check-card${state.kind === 'ready' ? ` ${state.report.status}` : ''}${state.kind === 'ready' && state.pushedAt ? ' flash' : ''}`;
  return (
    <section className={className} data-plugin="checking" key={state.kind === 'ready' ? state.pushedAt?.getTime() : state.kind}>
      {state.kind === 'loading' && <p className="muted">Calcul en cours…</p>}
      {state.kind === 'error' && <p className="muted">Rapport indisponible ({state.message}).</p>}
      {state.kind === 'removed' && <p className="muted">Produit supprimé.</p>}
      {state.kind === 'ready' && <CheckReportView report={state.report} pushedAt={state.pushedAt} />}
    </section>
  );
}
