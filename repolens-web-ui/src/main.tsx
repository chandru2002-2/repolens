import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import {
  applyResolvedTheme,
  readThemePreference,
  resolveTheme,
} from "./theme";
import "./styles.css";

applyResolvedTheme(resolveTheme(readThemePreference()));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
