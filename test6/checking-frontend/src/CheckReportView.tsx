import type { CheckReport, Metric } from './api/client';

const STATUS: Record<CheckReport['status'], string> = { OK: 'Conforme', WARNING: 'À surveiller', BLOCKING: 'Bloquant' };
const SEVERITY: Record<string, string> = { INFO: 'Info', WARNING: 'Avertissement', BLOCKING: 'Bloquant' };

const formatMetric = (m: Metric) =>
  `${m.value.toLocaleString('fr-FR', { minimumFractionDigits: m.unit === '€' ? 2 : 0, maximumFractionDigits: 2 })} ${m.unit}`;

/** Rendu piloté par les données du rapport : il ne connaît aucun module ext-checking-*. */
export function CheckReportView({ report, pushedAt }: { report: CheckReport; pushedAt?: Date }) {
  return (
    <>
      <header>
        <h3>Contrôle</h3>
        <span className={`check-status ${report.status}`}>{STATUS[report.status]}</span>
        <span className="check-score">{report.score}<small> / 100</small></span>
      </header>
      {report.metrics.length > 0 && (
        <table>
          <tbody>
            {report.metrics.map((m) => (
              <tr key={m.code} data-metric={m.code}><td>{m.label}</td><td className="num">{formatMetric(m)}</td></tr>
            ))}
          </tbody>
        </table>
      )}
      {report.findings.length === 0 ? <p className="muted">Aucun problème détecté.</p> : (
        <ul className="check-findings">
          {report.findings.map((f) => (
            <li key={f.rule} data-rule={f.rule}>
              <span className={`sev ${f.severity}`}>{SEVERITY[f.severity] ?? f.severity}</span>
              <span>{f.message}</span>
              {f.penalty > 0 && <span className="penalty">−{f.penalty}</span>}
            </li>
          ))}
        </ul>
      )}
      <p className="check-meta">
        Calcul : {report.contributors.join(', ')}
        {pushedAt && ` · recalculé à ${pushedAt.toLocaleTimeString('fr-FR')}`}
      </p>
    </>
  );
}
