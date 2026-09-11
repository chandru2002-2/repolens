import { useId } from "react";
import type { GraphFilterState, GraphViewMode } from "./graphModel";

type Props = {
  view: GraphViewMode;
  filters: GraphFilterState;
  onChange: (next: GraphFilterState) => void;
};

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  const id = useId();
  return (
    <label className="filter-toggle" htmlFor={id}>
      <input
        id={id}
        name={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

export function GraphFilters({ view, filters, onChange }: Props) {
  const patch = (partial: Partial<GraphFilterState>) =>
    onChange({ ...filters, ...partial });
  const core = view === "architecture" || view === "package" || view === "class";

  return (
    <form
      className="graph-filters"
      aria-label="Graph filters"
      onSubmit={(event) => event.preventDefault()}
    >
      <input
        className="filter-search"
        value={filters.query}
        onChange={(event) => patch({ query: event.target.value })}
        placeholder="Filter by name…"
        aria-label="Filter graph by name"
      />
      {core ? (
        <>
          <div className="filter-group">
            {view !== "architecture" ? (
              <Toggle
                label="Packages"
                checked={filters.showPackages}
                onChange={(showPackages) => patch({ showPackages })}
              />
            ) : null}
            {view === "class" ? (
              <>
                <Toggle
                  label="Classes"
                  checked={filters.showClasses}
                  onChange={(showClasses) => patch({ showClasses })}
                />
                <Toggle
                  label="Interfaces"
                  checked={filters.showInterfaces}
                  onChange={(showInterfaces) => patch({ showInterfaces })}
                />
                <Toggle
                  label="Enums"
                  checked={filters.showEnums}
                  onChange={(showEnums) => patch({ showEnums })}
                />
                <Toggle
                  label="Methods"
                  checked={filters.showMethods}
                  onChange={(showMethods) => patch({ showMethods })}
                />
                <Toggle
                  label="Fields"
                  checked={filters.showFields}
                  onChange={(showFields) => patch({ showFields })}
                />
              </>
            ) : null}
          </div>
          <div className="filter-group">
            <Toggle
              label="Depends on"
              checked={filters.edgeDependsOn}
              onChange={(edgeDependsOn) => patch({ edgeDependsOn })}
            />
            {view === "class" ? (
              <>
                <Toggle
                  label="Extends"
                  checked={filters.edgeExtends}
                  onChange={(edgeExtends) => patch({ edgeExtends })}
                />
                <Toggle
                  label="Implements"
                  checked={filters.edgeImplements}
                  onChange={(edgeImplements) => patch({ edgeImplements })}
                />
              </>
            ) : null}
            <Toggle
              label="Contains"
              checked={filters.edgeContains}
              onChange={(edgeContains) => patch({ edgeContains })}
            />
            <Toggle
              label="Internal only"
              checked={filters.internalOnly}
              onChange={(internalOnly) => patch({ internalOnly })}
            />
          </div>
        </>
      ) : (
        <p className="filter-hint">Name filter applies to the current specialized diagram.</p>
      )}
    </form>
  );
}
