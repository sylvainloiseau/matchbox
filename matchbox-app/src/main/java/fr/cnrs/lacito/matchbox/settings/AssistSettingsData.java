package fr.cnrs.lacito.matchbox.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Settings of the AI assistant, part of {@link SettingsData}. Immutable. API keys are not stored
 * here (settings files may be copied or shared).
 *
 * @param provider               "none", "anthropic", "openai" or "ollama"
 * @param baseUrl                server address, empty for the provider's default
 * @param fastModel              model for inline completion
 * @param smartModel             model for explanations and reviews
 * @param inlineEnabled          inline suggestions (ghost text) are enabled
 * @param trigger                "manual" (Ctrl+Space) or "auto" (after a pause in typing)
 * @param debounceMs             pause before an automatic suggestion
 * @param contextBefore          characters sent before the cursor
 * @param contextAfter           characters sent after the cursor
 * @param includeGrammarSource   send the ANTLR source of the grammars
 * @param suggestionPanelVisible the suggestions panel is shown
 * @param language               language of explanations
 * @param cloudConsentGiven      providers for which the user acknowledged that text is sent outside
 */
public record AssistSettingsData(
    String provider,
    String baseUrl,
    String fastModel,
    String smartModel,
    Boolean inlineEnabled,
    String trigger,
    Integer debounceMs,
    Integer contextBefore,
    Integer contextAfter,
    Boolean includeGrammarSource,
    Boolean suggestionPanelVisible,
    String language,
    List<String> cloudConsentGiven
) {

    public static final String MANUAL = "manual";
    public static final String AUTO = "auto";
    private static final Set<String> PROVIDERS = Set.of("none", "anthropic", "openai", "ollama");

    public static AssistSettingsData defaults() {
        return new AssistSettingsData("none", "", "", "", true, MANUAL, 800, 4000, 1000, false, true,
            "English", List.of());
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public AssistSettingsData validated(Consumer<String> problems) {
        AssistSettingsData d = defaults();
        String p = provider;
        if (p != null && !PROVIDERS.contains(p)) {
            problems.accept("Unknown assistant provider '" + p + "', assistant disabled");
            p = null;
        }
        String t = trigger;
        if (t != null && !t.equals(MANUAL) && !t.equals(AUTO)) {
            problems.accept("Unknown suggestion trigger '" + t + "', using " + d.trigger);
            t = null;
        }
        List<String> consent = new ArrayList<>();
        if (cloudConsentGiven != null) {
            for (String c : cloudConsentGiven) {
                if (c != null && PROVIDERS.contains(c) && !consent.contains(c)) consent.add(c);
            }
        }
        return new AssistSettingsData(
            p == null ? d.provider : p,
            baseUrl == null ? d.baseUrl : baseUrl.strip(),
            fastModel == null ? d.fastModel : fastModel.strip(),
            smartModel == null ? d.smartModel : smartModel.strip(),
            inlineEnabled == null ? d.inlineEnabled : inlineEnabled,
            t == null ? d.trigger : t,
            range(debounceMs, 100, 10_000, d.debounceMs, "debounceMs", problems),
            range(contextBefore, 200, 100_000, d.contextBefore, "contextBefore", problems),
            range(contextAfter, 0, 100_000, d.contextAfter, "contextAfter", problems),
            includeGrammarSource == null ? d.includeGrammarSource : includeGrammarSource,
            suggestionPanelVisible == null ? d.suggestionPanelVisible : suggestionPanelVisible,
            language == null || language.isBlank() ? d.language : language.strip(),
            List.copyOf(consent)
        );
    }

    private static int range(Integer value, int min, int max, int fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (value >= min && value <= max) return value;
        problems.accept("Invalid value " + value + " for " + name + ", using " + fallback);
        return fallback;
    }

    public boolean hasConsent(String providerId) {
        return cloudConsentGiven.contains(providerId);
    }

    public AssistSettingsData withConsent(String providerId) {
        if (hasConsent(providerId)) return this;
        List<String> c = new ArrayList<>(cloudConsentGiven);
        c.add(providerId);
        return new AssistSettingsData(provider, baseUrl, fastModel, smartModel, inlineEnabled, trigger, debounceMs,
            contextBefore, contextAfter, includeGrammarSource, suggestionPanelVisible, language, List.copyOf(c));
    }

    public AssistSettingsData withInlineEnabled(boolean enabled) {
        return new AssistSettingsData(provider, baseUrl, fastModel, smartModel, enabled, trigger, debounceMs,
            contextBefore, contextAfter, includeGrammarSource, suggestionPanelVisible, language, cloudConsentGiven);
    }

    public AssistSettingsData withSuggestionPanelVisible(boolean visible) {
        return new AssistSettingsData(provider, baseUrl, fastModel, smartModel, inlineEnabled, trigger, debounceMs,
            contextBefore, contextAfter, includeGrammarSource, visible, language, cloudConsentGiven);
    }
}
