export type ThemePreference = "light" | "dark" | "auto";
export type ResolvedTheme = "light" | "dark";

const STORAGE_KEY = "repolens.theme";

export function readThemePreference(): ThemePreference {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === "light" || value === "dark" || value === "auto") {
      return value;
    }
  } catch {
    // ignore
  }
  return "auto";
}

export function writeThemePreference(preference: ThemePreference): void {
  try {
    localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    // ignore
  }
}

export function systemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function resolveTheme(preference: ThemePreference): ResolvedTheme {
  if (preference === "auto") {
    return systemPrefersDark() ? "dark" : "light";
  }
  return preference;
}

export function applyResolvedTheme(theme: ResolvedTheme): void {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
}
