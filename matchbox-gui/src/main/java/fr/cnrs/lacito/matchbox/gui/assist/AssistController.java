package fr.cnrs.lacito.matchbox.gui.assist;

import fr.cnrs.lacito.matchbox.assist.AssistContext;
import fr.cnrs.lacito.matchbox.assist.AssistOptions;
import fr.cnrs.lacito.matchbox.assist.AssistService;
import fr.cnrs.lacito.matchbox.assist.privacy.PrivacyPolicy;
import fr.cnrs.lacito.matchbox.assist.privacy.PrivacyPolicy.Decision;
import fr.cnrs.lacito.matchbox.assist.prompt.FewShotExample;
import fr.cnrs.lacito.matchbox.assist.prompt.FewShotSelector;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptPrefix;
import fr.cnrs.lacito.matchbox.assist.provider.CompletionProvider;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderConfig;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderFactory;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import fr.cnrs.lacito.matchbox.assist.validation.EditValidator;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.AssistSettingsData;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * The assistant as used by the GUI: builds the {@link AssistService} from the settings, decides
 * whether text may be sent (privacy), and builds the context of a request from the project.
 */
public final class AssistController {

    private final UserSettings settings;
    private final ApiKeyStore keys;
    private final ProviderFactory factory = new ProviderFactory();
    private final EditValidator validator = new EditValidator();
    private final PromptPrefix.Cache prefixes = new PromptPrefix.Cache();
    private final Supplier<Window> owner;
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper();

    private Project project;
    /** Examples of the project, chosen once per project (frozen so that the prompt prefix stays stable). */
    private List<FewShotExample> examples;
    private AssistService service;

    public AssistController(UserSettings settings, Supplier<Window> owner) {
        this.settings = settings;
        this.owner = owner;
        this.keys = new ApiKeyStore(settings.directories().configDir());
        settings.assistProperty().addListener((obs, o, n) -> {
            service = null;
            if (o == null || n.includeGrammarSource() != o.includeGrammarSource()) {
                prefixes.clear();
            }
            updateStatus();
        });
        updateStatus();
    }

    public UserSettings settings() {
        return settings;
    }

    public AssistSettingsData data() {
        return settings.assistProperty().get();
    }

    public ApiKeyStore keys() {
        return keys;
    }

    public EditValidator validator() {
        return validator;
    }

    public ProviderFactory factory() {
        return factory;
    }

    public void setProject(Project project) {
        this.project = project;
        this.examples = null;
        prefixes.clear();
        updateStatus();
    }

    /** "AI: Claude ☁", "AI: Ollama (local)", "AI: off"… for the editor toolbar. */
    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    public void updateStatus() {
        ProviderKind kind = kind();
        if (kind == ProviderKind.NONE) {
            status.set("AI: off");
            return;
        }
        boolean cloud = PrivacyPolicy.isCloud(kind, data().baseUrl());
        String name = switch (kind) {
            case ANTHROPIC -> "Claude";
            case OPENAI -> "ChatGPT";
            case OLLAMA -> "Ollama";
            case NONE -> "";
        };
        if (cloud && project != null && !project.isCloudAssistAllowed()) {
            status.set("AI: " + name + " ☁ – blocked (project allows local models only)");
        } else {
            status.set("AI: " + name + (cloud ? " ☁" : " (local)"));
        }
    }

    public ProviderKind kind() {
        return ProviderKind.fromId(data().provider()).orElse(ProviderKind.NONE);
    }

    // ------------------------------------------------------------------
    // Privacy
    // ------------------------------------------------------------------

    /** The privacy decision, without asking anything. */
    public Decision decision() {
        ProviderKind kind = kind();
        return PrivacyPolicy.decide(kind, data().baseUrl(), project == null || project.isCloudAssistAllowed(),
            data().hasConsent(kind.id()));
    }

    /**
     * True if a request may be sent now. Asks for consent the first time a cloud provider is used,
     * and explains why not otherwise. For actions the user asked for explicitly.
     */
    public boolean ensureAllowed() {
        Decision d = decision();
        switch (d) {
            case ALLOWED:
                return true;
            case DISABLED: {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "No AI provider is selected. Choose one in File › AI assistant…");
                alert.initOwner(owner.get());
                alert.setHeaderText("AI assistant disabled");
                alert.showAndWait();
                return false;
            }
            case BLOCKED_BY_PROJECT: {
                Alert alert = new Alert(Alert.AlertType.WARNING,
                    "This project allows local AI models only (see File › Edit project…). " +
                    "Select Ollama in File › AI assistant…, or change the project settings.");
                alert.initOwner(owner.get());
                alert.setHeaderText("Cloud AI providers are not allowed for this project");
                alert.showAndWait();
                return false;
            }
            case NEEDS_CONSENT:
            default: {
                CloudConsentDialog.Answer answer = CloudConsentDialog.ask(owner.get(), kind().displayName());
                if (answer == CloudConsentDialog.Answer.CONTINUE) {
                    settings.assistProperty().set(data().withConsent(kind().id()));
                    return true;
                }
                if (answer == CloudConsentDialog.Answer.USE_LOCAL) {
                    openSettings(ProviderKind.OLLAMA);
                }
                return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Service and context
    // ------------------------------------------------------------------

    /** The service for the current settings, or empty if no usable provider is configured. */
    public Optional<AssistService> service() {
        if (service != null) {
            return Optional.of(service);
        }
        ProviderKind kind = kind();
        if (kind == ProviderKind.NONE) {
            return Optional.empty();
        }
        AssistSettingsData d = data();
        String key = keys.get(kind).orElse("");
        String fastModel = d.fastModel().isBlank() ? kind.defaultFastModel() : d.fastModel();
        String smartModel = d.smartModel().isBlank() ? (kind.defaultSmartModel().isBlank() ? fastModel : kind.defaultSmartModel())
            : d.smartModel();
        if (fastModel.isBlank()) {
            fastModel = smartModel;
        }
        if (fastModel.isBlank()) {
            return Optional.empty();
        }
        CompletionProvider fast = factory.create(new ProviderConfig(kind, d.baseUrl(), fastModel, key));
        CompletionProvider smart = factory.create(new ProviderConfig(kind, d.baseUrl(), smartModel, key));
        service = new AssistService(fast, smart, validator,
            new AssistOptions(d.contextBefore(), d.contextAfter(), 15, d.language()));
        return Optional.of(service);
    }

    /** Explains what is missing to use the assistant (no provider, no model, no key). */
    public String whyUnavailable() {
        ProviderKind kind = kind();
        if (kind == ProviderKind.NONE) {
            return "No AI provider is selected (File › AI assistant…).";
        }
        if (kind.needsApiKey() && keys.get(kind).isEmpty()) {
            return "No API key for " + kind.displayName() + " (File › AI assistant…).";
        }
        return "No model is selected for " + kind.displayName() + " (File › AI assistant…).";
    }

    /** Context of a request on the edited file. */
    public AssistContext context(Path file, String text, int caret) {
        List<GrammarPlugin> grammars = project == null ? List.of() : project.getGrammarsForFile(file);
        PromptPrefix prefix = prefixes.get(grammars, examples(), data().includeGrammarSource());
        List<fr.cnrs.lacito.matchbox.grammarapi.ParseError> errors =
            project == null ? List.of() : project.getCorpus().errorsFor(file);
        return new AssistContext(file, text, caret, grammars, errors, prefix);
    }

    public List<GrammarPlugin> grammarsFor(Path file) {
        return project == null ? List.of() : project.getGrammarsForFile(file);
    }

    private List<FewShotExample> examples() {
        if (examples == null) {
            examples = project == null ? List.of() : selectExamples(project.getCorpus());
        }
        return examples;
    }

    private static List<FewShotExample> selectExamples(Corpus corpus) {
        List<FewShotSelector.ParsedFile> files = new ArrayList<>();
        for (Path f : corpus.files()) {
            corpus.parsedText(f).ifPresent(text ->
                files.add(new FewShotSelector.ParsedFile(f, text, corpus.objectsFor(f), corpus.errorsFor(f))));
        }
        return FewShotSelector.select(files);
    }

    /** Chooses the project examples again (e.g. after the notes were corrected). */
    public void refreshExamples() {
        examples = null;
        prefixes.clear();
    }

    // ------------------------------------------------------------------
    // Dialog
    // ------------------------------------------------------------------

    public void openSettings() {
        openSettings(null);
    }

    /** Opens the settings dialog, with a provider preselected if not null. */
    public void openSettings(ProviderKind preselected) {
        new AiModelDialog(this, owner.get(), project).showAndWait(preselected);
        updateStatus();
    }

    /** Forgets the service, e.g. after an API key changed. */
    public void invalidate() {
        service = null;
        updateStatus();
    }

    public void close() {
        validator.close();
    }
}
