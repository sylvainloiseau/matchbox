package fr.cnrs.lacito.matchbox.gui.assist;

import fr.cnrs.lacito.matchbox.assist.privacy.PrivacyPolicy;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;

/** Asks, the first time a cloud provider is used, whether text may be sent to it. */
final class CloudConsentDialog {

    enum Answer { CONTINUE, USE_LOCAL, CANCEL }

    private CloudConsentDialog() {}

    static Answer ask(Window owner, String providerName) {
        ButtonType continueButton = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        ButtonType localButton = new ButtonType("Use a local model instead", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", continueButton, localButton, cancelButton);
        alert.initOwner(owner);
        alert.setTitle("AI assistant");
        alert.setHeaderText("Send parts of your notes to " + providerName + "?");
        Label content = new Label(
            providerName + " runs outside this computer: the text it receives is processed on the " +
            "provider's servers, under its terms of service and data retention policy.\n\n" +
            PrivacyPolicy.whatIsSent() + "\n\n" +
            "If your notes contain personal data of consultants or material under consent " +
            "restrictions, use a local model (Ollama), which keeps everything on this computer, or " +
            "disallow cloud providers for the project (Current project › Project properties…).\n\n" +
            "This question is asked once per provider."
        );
        content.setWrapText(true);
        content.setMaxWidth(520);
        alert.getDialogPane().setContent(content);
        ButtonType result = alert.showAndWait().orElse(cancelButton);
        if (result == continueButton) return Answer.CONTINUE;
        if (result == localButton) return Answer.USE_LOCAL;
        return Answer.CANCEL;
    }
}
