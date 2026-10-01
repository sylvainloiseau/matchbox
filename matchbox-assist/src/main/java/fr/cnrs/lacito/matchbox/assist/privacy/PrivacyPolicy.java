package fr.cnrs.lacito.matchbox.assist.privacy;

import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Whether text may be sent to a provider. Field notes may contain consultants' personal data:
 * a provider that is not on this computer is used only after the user acknowledged it, and never
 * for a project that allows local models only.
 */
public final class PrivacyPolicy {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0");

    public enum Decision {
        /** No provider is selected. */
        DISABLED,
        /** Text may be sent. */
        ALLOWED,
        /** Cloud provider not yet acknowledged by the user. */
        NEEDS_CONSENT,
        /** The project allows local providers only. */
        BLOCKED_BY_PROJECT
    }

    private PrivacyPolicy() {}

    /** True if the provider sends text outside this computer. */
    public static boolean isCloud(ProviderKind kind, String baseUrl) {
        if (kind == ProviderKind.NONE) {
            return false;
        }
        String url = baseUrl == null || baseUrl.isBlank() ? kind.defaultBaseUrl() : baseUrl.strip();
        try {
            String host = URI.create(url).getHost();
            return host == null || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static Decision decide(ProviderKind kind, String baseUrl, boolean projectAllowsCloud, boolean consentGiven) {
        if (kind == ProviderKind.NONE) {
            return Decision.DISABLED;
        }
        if (!isCloud(kind, baseUrl)) {
            return Decision.ALLOWED;
        }
        if (!projectAllowsCloud) {
            return Decision.BLOCKED_BY_PROJECT;
        }
        return consentGiven ? Decision.ALLOWED : Decision.NEEDS_CONSENT;
    }

    /** Explanation of what is sent, for the consent dialog and the settings. */
    public static String whatIsSent() {
        return "When you ask for a suggestion, an explanation or a review, Matchbox sends:\n" +
            "• the text around the cursor, the reviewed block or the lines around the error;\n" +
            "• a few examples taken from the files of the current project;\n" +
            "• the documentation of the grammars of the file.\n" +
            "Nothing is sent otherwise.";
    }
}
