import type { AnalysisResponse } from "./api";
import {
  EMPTY_ENDPOINTS,
  EMPTY_TESTS,
  EMPTY_TRACES,
  formatEvidence,
  formatLocation,
  intelligenceLists,
  labelForEntity,
  testSubjectsFromGraph,
  traceKindLabel,
  type IntelligenceTab,
} from "./intelligence";

type Props = {
  result: AnalysisResponse;
  tab: IntelligenceTab;
  onSelectEntity: (entityId: string) => void;
};

export function IntelligencePanel({ result, tab, onSelectEntity }: Props) {
  const lists = intelligenceLists(result);

  if (tab === "endpoints") {
    return (
      <div className="intel-stage">
        <p className="intel-disclaimer">Static endpoint declarations from the analysis API.</p>
        {lists.endpoints.length === 0 ? (
          <p className="diagram-empty" role="status">
            {EMPTY_ENDPOINTS}
          </p>
        ) : (
          <ul className="intel-list">
            {lists.endpoints.map((endpoint) => (
              <li key={endpoint.id} className="intel-item">
                <div className="intel-item-head">
                  <span className="intel-method">{endpoint.httpMethod}</span>
                  <span className="intel-path mono">{endpoint.path}</span>
                </div>
                <dl className="detail-list">
                  {endpoint.handlerMethodId ? (
                    <div>
                      <dt>Handler</dt>
                      <dd>
                        <EntityLink
                          entityId={endpoint.handlerMethodId}
                          nodes={result.graph.nodes}
                          onSelectEntity={onSelectEntity}
                        />
                      </dd>
                    </div>
                  ) : null}
                  {endpoint.ownerTypeId ? (
                    <div>
                      <dt>Owner</dt>
                      <dd>
                        <EntityLink
                          entityId={endpoint.ownerTypeId}
                          nodes={result.graph.nodes}
                          onSelectEntity={onSelectEntity}
                        />
                      </dd>
                    </div>
                  ) : null}
                  {formatLocation(endpoint.location) ? (
                    <div>
                      <dt>Location</dt>
                      <dd className="mono">{formatLocation(endpoint.location)}</dd>
                    </div>
                  ) : null}
                  <div>
                    <dt>Evidence</dt>
                    <dd>{formatEvidence(endpoint.evidence)}</dd>
                  </div>
                </dl>
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  if (tab === "tests") {
    return (
      <div className="intel-stage">
        <p className="intel-disclaimer">
          Discovered test facts from the analysis API. Subjects are shown only when TESTS
          relationships are already present on the result graph.
        </p>
        {lists.tests.length === 0 ? (
          <p className="diagram-empty" role="status">
            {EMPTY_TESTS}
          </p>
        ) : (
          <ul className="intel-list">
            {lists.tests.map((test) => {
              const subjects = testSubjectsFromGraph(result, test);
              return (
                <li key={test.id} className="intel-item">
                  <div className="intel-item-head">
                    <span className="intel-path mono">{test.symbolId}</span>
                    {test.frameworkHint ? (
                      <span className="intel-hint">{test.frameworkHint}</span>
                    ) : null}
                  </div>
                  <dl className="detail-list">
                    {formatLocation(test.location) ? (
                      <div>
                        <dt>Location</dt>
                        <dd className="mono">{formatLocation(test.location)}</dd>
                      </div>
                    ) : null}
                    <div>
                      <dt>Evidence</dt>
                      <dd>{formatEvidence(test.evidence)}</dd>
                    </div>
                  </dl>
                  <h3 className="intel-subhead">Inferred subjects</h3>
                  {subjects.length === 0 ? (
                    <p className="panel-hint">
                      No TESTS relationships for this test are present on the analysis graph.
                    </p>
                  ) : (
                    <ul className="rel-list">
                      {subjects.map((subject) => (
                        <li key={subject.id}>
                          <button
                            type="button"
                            className="linkish"
                            onClick={() =>
                              onSelectEntity(subject.sourceEntityId ?? subject.id)
                            }
                          >
                            {subject.label}
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    );
  }

  return (
    <div className="intel-stage">
      <p className="intel-disclaimer">
        Static traces from the analysis API. These are not runtime execution paths.
      </p>
      {lists.traces.length === 0 ? (
        <p className="diagram-empty" role="status">
          {EMPTY_TRACES}
        </p>
      ) : (
        <ul className="intel-list">
          {lists.traces.map((trace) => (
            <li key={trace.id} className="intel-item">
              <div className="intel-item-head">
                <span className="intel-path mono">{trace.endpointId}</span>
                <span className="intel-hint">{traceKindLabel(trace)}</span>
              </div>
              <dl className="detail-list">
                <div>
                  <dt>Confidence</dt>
                  <dd>{trace.confidence}</dd>
                </div>
                <div>
                  <dt>Unresolved</dt>
                  <dd>{trace.unresolved ? "yes" : "no"}</dd>
                </div>
              </dl>
              <ol className="hop-list">
                {trace.hops.map((hop, index) => (
                  <li key={`${trace.id}-${index}-${hop.entityId}`}>
                    <span className="hop-index">{index + 1}</span>
                    <span className="hop-role">{hop.role}</span>
                    <EntityLink
                      entityId={hop.entityId}
                      nodes={result.graph.nodes}
                      onSelectEntity={onSelectEntity}
                    />
                    <span className="hop-meta">
                      confidence {hop.confidence}
                      {hop.resolved ? "" : " · unresolved"}
                    </span>
                  </li>
                ))}
              </ol>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function EntityLink({
  entityId,
  nodes,
  onSelectEntity,
}: {
  entityId: string;
  nodes: AnalysisResponse["graph"]["nodes"];
  onSelectEntity: (entityId: string) => void;
}) {
  const label = labelForEntity(nodes, entityId);
  return (
    <button type="button" className="linkish" onClick={() => onSelectEntity(entityId)}>
      {label ?? entityId}
    </button>
  );
}
