package io.repolens.parse.profile;

import java.util.List;

public final class LanguageProfiles {

    private static final List<LanguageProfile> DEFAULT = List.of(
            new JavaLanguageProfile(),
            new JavaScriptLanguageProfile(),
            new TypeScriptLanguageProfile(),
            new PythonLanguageProfile(),
            new GoLanguageProfile(),
            new RustLanguageProfile(),
            new CSharpLanguageProfile(),
            new KotlinLanguageProfile()
    );

    private LanguageProfiles() {
    }

    public static List<LanguageProfile> defaults() {
        return DEFAULT;
    }

    public static LanguageProfile find(String relativePath) {
        for (LanguageProfile profile : DEFAULT) {
            if (profile.supports(relativePath)) {
                return profile;
            }
        }
        return null;
    }
}
