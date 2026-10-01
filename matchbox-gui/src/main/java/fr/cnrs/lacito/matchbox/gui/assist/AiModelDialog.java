package fr.cnrs.lacito.matchbox.gui.assist;

import fr.cnrs.lacito.matchbox.assist.AssistService;
import fr.cnrs.lacito.matchbox.assist.privacy.PrivacyPolicy;
import fr.cnrs.lacito.matchbox.assist.provider.CompletionProvider;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderConfig;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.AssistSettingsData;
import java.io.IOException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Choice of the AI provider and models (File › AI assistant…). Values are applied on OK; API
 * keys are stored by {@link ApiKeyStore}, never in the settings file.
 */
final class AiModelDialog {

    private static final List<String> LANGUAGES = List.of("English", "French", "Spanish", "German", "Portuguese");

    private final AssistController controller;
    private final Window owner;
    private final Project project;
    private Stage stage;

    private final ToggleGroup providerGroup = new ToggleGroup();
    private final PasswordField keyField = new PasswordField();
    private final TextField keyVisibleField = new TextField();
    private final ToggleButton showKey = new ToggleButton("Show");
    private final Label keyInfo = new Label();
    private final TextField baseUrlField = new TextField();
    private final ComboBox<String> fastModel = new ComboBox<>();
    private final ComboBox<String> smartModel = new ComboBox<>();
    private final Label modelsInfo = new Label();
    private final Label testResult = new Label();
    private final CheckBox inlineEnabled = new CheckBox("Show inline suggestions (grey text, Tab to accept)");
    private final RadioButton manualTrigger = new RadioButton("On request (Ctrl+Space)");
    private final RadioButton autoTrigger = new RadioButton("Automatically after a pause of");
    private final Spinner<Integer> debounce = new Spinner<>(100, 10_000, 800, 100);
    private final Spinner<Integer> contextBefore = new Spinner<>(200, 100_000, 4000, 500);
    private final Spinner<Integer> contextAfter = new Spinner<>(0, 100_000, 1000, 500);
    private final CheckBox includeSource = new CheckBox("Include the ANTLR source of the grammars in prompts");
    private final ComboBox<String> language = new ComboBox<>();
    private final Label privacy = new Label();

    /** Provider shown in the key field (the key is kept per provider while the dialog is open). */
    private ProviderKind keyProvider;
    private final java.util.Map<ProviderKind, String> editedKeys = new java.util.EnumMap<>(ProviderKind.class);

    AiModelDialog(AssistController controller, Window owner, Project project) {
        this.controller = controller;
        this.owner = owner;
        this.project = project;
    }

    void showAndWait(ProviderKind preselected) {
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("AI assistant");

        AssistSettingsData data = controller.data();
        VBox root = new VBox(12);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(providerSection(), connectionSection(), suggestionsSection(), privacy, buttons());
        privacy.setWrapText(true);
        privacy.setStyle("-fx-border-color: #cccccc; -fx-padding: 8; -fx-background-color: #f7f7f7;");

        load(data);
        if (preselected != null) {
            select(preselected);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll, 620, 720));
        stage.showAndWait();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private VBox providerSection() {
        VBox box = section("Provider");
        HBox radios = new HBox(12);
        for (ProviderKind kind : ProviderKind.values()) {
            RadioButton r = new RadioButton(kind.displayName());
            r.setUserData(kind);
            r.setToggleGroup(providerGroup);
            radios.getChildren().add(r);
        }
        providerGroup.selectedToggleProperty().addListener((obs, o, n) -> onProviderChanged(
            o == null ? null : (ProviderKind) o.getUserData(), n == null ? ProviderKind.NONE : (ProviderKind) n.getUserData()));
        box.getChildren().add(radios);
        return box;
    }

    private VBox connectionSection() {
        VBox box = section("Connection");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        keyVisibleField.textProperty().bindBidirectional(keyField.textProperty());
        keyVisibleField.visibleProperty().bind(showKey.selectedProperty());
        keyField.visibleProperty().bind(showKey.selectedProperty().not());
        StackPane keyStack = new StackPane(keyField, keyVisibleField);
        HBox keyBox = new HBox(6, keyStack, showKey);
        HBox.setHgrow(keyStack, Priority.ALWAYS);
        keyInfo.setWrapText(true);
        keyInfo.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        baseUrlField.textProperty().addListener((obs, o, n) -> updatePrivacy());

        for (ComboBox<String> c : List.of(fastModel, smartModel)) {
            c.setEditable(true);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        Button refresh = new Button("Refresh models");
        refresh.setOnAction(e -> refreshModels());
        Button test = new Button("Test connection");
        test.setOnAction(e -> testConnection());
        modelsInfo.setWrapText(true);
        modelsInfo.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        testResult.setWrapText(true);

        grid.addRow(0, new Label("API key"), keyBox);
        grid.add(keyInfo, 1, 1);
        grid.addRow(2, new Label("Server address"), baseUrlField);
        grid.addRow(3, new Label("Fast model"), fastModel);
        grid.add(hint("Used for inline suggestions, which must be quick."), 1, 4);
        grid.addRow(5, new Label("Smart model"), smartModel);
        grid.add(hint("Used to explain errors and review passages."), 1, 6);
        grid.add(new HBox(8, refresh, test), 1, 7);
        grid.add(modelsInfo, 1, 8);
        grid.add(testResult, 1, 9);
        GridPane.setHgrow(keyBox, Priority.ALWAYS);
        GridPane.setHgrow(baseUrlField, Priority.ALWAYS);
        box.getChildren().add(grid);
        return box;
    }

    private VBox suggestionsSection() {
        VBox box = section("Suggestions");
        ToggleGroup trigger = new ToggleGroup();
        manualTrigger.setToggleGroup(trigger);
        autoTrigger.setToggleGroup(trigger);
        debounce.setEditable(true);
        debounce.setPrefWidth(100);
        debounce.disableProperty().bind(autoTrigger.selectedProperty().not());
        HBox auto = new HBox(6, autoTrigger, debounce, new Label("ms"));
        auto.setAlignment(Pos.CENTER_LEFT);

        contextBefore.setEditable(true);
        contextAfter.setEditable(true);
        HBox context = new HBox(6, new Label("Characters sent before the cursor"), contextBefore,
            new Label("after"), contextAfter);
        context.setAlignment(Pos.CENTER_LEFT);

        language.getItems().setAll(LANGUAGES);
        language.setEditable(true);
        HBox lang = new HBox(6, new Label("Language of explanations"), language);
        lang.setAlignment(Pos.CENTER_LEFT);

        for (Spinner<Integer> spinner : List.of(debounce, contextBefore, contextAfter)) {
            // typed values are committed only on Enter otherwise
            spinner.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) spinner.increment(0);
            });
        }
        box.getChildren().addAll(inlineEnabled, manualTrigger, auto, context, includeSource,
            hint("Off by default: models read the documentation of the grammars better than their source."), lang);
        return box;
    }

    private HBox buttons() {
        Button ok = new Button("OK");
        ok.setDefaultButton(true);
        ok.setPrefWidth(80);
        ok.setOnAction(e -> {
            if (save()) stage.close();
        });
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setPrefWidth(80);
        cancel.setOnAction(e -> stage.close());
        HBox box = new HBox(10, ok, cancel);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static VBox section(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(6, label);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");
        return box;
    }

    private static Label hint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        return l;
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    private void load(AssistSettingsData d) {
        ProviderKind kind = ProviderKind.fromId(d.provider()).orElse(ProviderKind.NONE);
        baseUrlField.setText(d.baseUrl());
        fastModel.setValue(d.fastModel());
        smartModel.setValue(d.smartModel());
        inlineEnabled.setSelected(d.inlineEnabled());
        (AssistSettingsData.AUTO.equals(d.trigger()) ? autoTrigger : manualTrigger).setSelected(true);
        debounce.getValueFactory().setValue(d.debounceMs());
        contextBefore.getValueFactory().setValue(d.contextBefore());
        contextAfter.getValueFactory().setValue(d.contextAfter());
        includeSource.setSelected(d.includeGrammarSource());
        language.setValue(d.language());
        select(kind);
        // the stored values, not the defaults of the provider
        baseUrlField.setText(d.baseUrl());
        fastModel.setValue(d.fastModel().isBlank() ? kind.defaultFastModel() : d.fastModel());
        smartModel.setValue(d.smartModel().isBlank() ? kind.defaultSmartModel() : d.smartModel());
    }

    private void select(ProviderKind kind) {
        providerGroup.getToggles().stream().filter(t -> t.getUserData() == kind).findFirst()
            .ifPresent(providerGroup::selectToggle);
    }

    private ProviderKind selectedKind() {
        return providerGroup.getSelectedToggle() == null ? ProviderKind.NONE
            : (ProviderKind) providerGroup.getSelectedToggle().getUserData();
    }

    private void onProviderChanged(ProviderKind previous, ProviderKind kind) {
        if (previous != null && keyProvider == previous) {
            editedKeys.put(previous, keyField.getText());
        }
        keyProvider = kind;
        keyField.setText(editedKeys.computeIfAbsent(kind, k -> controller.keys().stored(k)));
        boolean needsKey = kind.needsApiKey();
        keyField.setDisable(!needsKey);
        keyVisibleField.setDisable(!needsKey);
        showKey.setDisable(!needsKey);
        if (!needsKey) {
            keyInfo.setText(kind == ProviderKind.OLLAMA ? "No key needed for a local Ollama server." : "");
        } else if (controller.keys().isFromEnvironment(kind)) {
            keyInfo.setText("Using the environment variable " + kind.apiKeyVariable() +
                " (it takes precedence over a key entered here).");
        } else {
            keyInfo.setText("Stored in " + controller.keys().file() + ", readable by you only; or set the " +
                "environment variable " + kind.apiKeyVariable() + ".");
        }
        boolean none = kind == ProviderKind.NONE;
        baseUrlField.setDisable(none);
        fastModel.setDisable(none);
        smartModel.setDisable(none);
        if (previous != null && previous != kind) {
            baseUrlField.setText("");
            fastModel.getItems().clear();
            smartModel.getItems().clear();
            fastModel.setValue(kind.defaultFastModel());
            smartModel.setValue(kind.defaultSmartModel());
        }
        baseUrlField.setPromptText(kind.defaultBaseUrl());
        modelsInfo.setText(kind == ProviderKind.OLLAMA
            ? "Install Ollama (ollama.com) and download a model, e.g. \"ollama pull qwen3:8b\", then click Refresh models."
            : "");
        testResult.setText("");
        updatePrivacy();
    }

    private void updatePrivacy() {
        ProviderKind kind = selectedKind();
        if (kind == ProviderKind.NONE) {
            privacy.setText("The assistant is disabled: nothing is sent anywhere.");
            return;
        }
        boolean cloud = PrivacyPolicy.isCloud(kind, baseUrlField.getText());
        StringBuilder b = new StringBuilder();
        if (cloud) {
            b.append("☁ ").append(kind.displayName()).append(" runs outside this computer. ");
            b.append("You will be asked to confirm before the first request.\n\n");
        } else {
            b.append("This provider runs on this computer: your notes do not leave it.\n\n");
        }
        b.append(PrivacyPolicy.whatIsSent());
        if (cloud && project != null && !project.isCloudAssistAllowed()) {
            b.append("\n\n⚠ The current project allows local models only: this provider will not be used for it.");
        }
        privacy.setText(b.toString());
    }

    /** A provider for the values of the dialog (not yet saved). */
    private CompletionProvider providerFromFields(String model) {
        ProviderKind kind = selectedKind();
        String key = keyField.getText().isBlank() ? controller.keys().get(kind).orElse("") : keyField.getText();
        if (controller.keys().isFromEnvironment(kind)) {
            key = controller.keys().get(kind).orElse("");
        }
        return controller.factory().create(new ProviderConfig(kind, baseUrlField.getText(), model, key));
    }

    private boolean cloudBlocked() {
        ProviderKind kind = selectedKind();
        return PrivacyPolicy.isCloud(kind, baseUrlField.getText()) && project != null && !project.isCloudAssistAllowed();
    }

    private void refreshModels() {
        if (selectedKind() == ProviderKind.NONE) return;
        modelsInfo.setText("Loading the models…");
        FxAssist.onFx(providerFromFields("").listModels(), models -> {
            String fast = fastModel.getValue();
            String smart = smartModel.getValue();
            fastModel.getItems().setAll(models);
            smartModel.getItems().setAll(models);
            fastModel.setValue(fast);
            smartModel.setValue(smart);
            if (models.isEmpty()) {
                modelsInfo.setText(selectedKind() == ProviderKind.OLLAMA
                    ? "No model installed. Download one with \"ollama pull <model>\" (e.g. qwen3:8b)."
                    : "No model available with this key.");
            } else {
                modelsInfo.setText(models.size() + " models available.");
            }
        }, error -> modelsInfo.setText("✗ " + FxAssist.message(error)));
    }

    private void testConnection() {
        String model = fastModel.getValue() == null ? "" : fastModel.getValue().strip();
        if (selectedKind() == ProviderKind.NONE || model.isEmpty()) {
            testResult.setText("Select a provider and a model first.");
            return;
        }
        // a test sends no notes, but still goes to the provider: say so if the project forbids it
        testResult.setText("Testing " + model + "…" + (cloudBlocked() ? " (no notes are sent)" : ""));
        FxAssist.onFx(AssistService.test(providerFromFields(model)),
            answer -> testResult.setText("✓ " + model + " answered."),
            error -> testResult.setText("✗ " + FxAssist.message(error)));
    }

    private boolean save() {
        ProviderKind kind = selectedKind();
        editedKeys.put(kind, keyField.getText());
        try {
            for (var e : editedKeys.entrySet()) {
                if (e.getKey().needsApiKey() && !e.getValue().equals(controller.keys().stored(e.getKey()))) {
                    controller.keys().set(e.getKey(), e.getValue());
                }
            }
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Cannot store the API key: " + ex.getMessage());
            alert.initOwner(stage);
            alert.showAndWait();
            return false;
        }
        AssistSettingsData old = controller.data();
        controller.settings().assistProperty().set(new AssistSettingsData(
            kind.id(),
            baseUrlField.getText(),
            fastModel.getValue() == null ? "" : fastModel.getValue(),
            smartModel.getValue() == null ? "" : smartModel.getValue(),
            inlineEnabled.isSelected(),
            autoTrigger.isSelected() ? AssistSettingsData.AUTO : AssistSettingsData.MANUAL,
            debounce.getValue(),
            contextBefore.getValue(),
            contextAfter.getValue(),
            includeSource.isSelected(),
            old.suggestionPanelVisible(),
            language.getValue() == null || language.getValue().isBlank() ? "English" : language.getValue(),
            old.cloudConsentGiven()
        ).validated(p -> {}));
        controller.invalidate();
        return true;
    }
}
