
import type { AnalysisResponse } from "./api";

type AnalysisWarningsProps = {
  result: AnalysisResponse;
};

export default function AnalysisWarnings({
  result,
}: AnalysisWarningsProps) {
  const warnings = result.results.flatMap((analyzer) =>
    analyzer.findings
      .filter(
        (finding) =>
          finding.severity.toLowerCase() === "warning",
      )
      .map((finding) => ({
        id: finding.id,
        message: finding.message,
      })),
  );

  if (warnings.length === 0) {
    return null;
  }

  return (
    <section
      className="analysis-warnings"
      aria-labelledby="analysis-warnings-title"
    >
      <h2 id="analysis-warnings-title">
        Analysis warnings ({warnings.length})
      </h2>
      <ul>
        {warnings.map((warning) => (
          <li key={warning.id}>{warning.message}</li>
        ))}
      </ul>
    </section>
  );
}
