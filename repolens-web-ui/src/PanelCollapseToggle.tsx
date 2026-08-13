type Props = {
  collapsed: boolean;
  /** Which side of the workspace the panel sits on — drives chevron direction. */
  side: "start" | "end";
  labelExpand: string;
  labelCollapse: string;
  controlsId: string;
  onToggle: () => void;
};

/**
 * Animated vector toggle for panel collapse/expand.
 * Chevron points toward the graph when the panel can expand, and away when collapsing.
 */
export function PanelCollapseToggle({
  collapsed,
  side,
  labelExpand,
  labelCollapse,
  controlsId,
  onToggle,
}: Props) {
  const label = collapsed ? labelExpand : labelCollapse;
  // Expanded: chevron points outward (away from graph). Collapsed: points inward to expand.
  const pointsOut = !collapsed;

  return (
    <button
      type="button"
      className={
        collapsed
          ? `panel-collapse-btn side-${side} is-collapsed`
          : `panel-collapse-btn side-${side}`
      }
      onClick={onToggle}
      aria-expanded={!collapsed}
      aria-controls={controlsId}
      aria-label={label}
      title={label}
    >
      <svg
        className={pointsOut ? "panel-collapse-icon points-out" : "panel-collapse-icon points-in"}
        viewBox="0 0 16 16"
        width="14"
        height="14"
        aria-hidden="true"
        focusable="false"
      >
        <path
          className="panel-collapse-chevron"
          d="M10.2 2.4 5.6 8l4.6 5.6"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="square"
          strokeLinejoin="miter"
        />
      </svg>
    </button>
  );
}
