package fr.cnrs.lacito.matchbox.settings;

/**
 * Settings of the problem tables of File Mode (Parsing errors, Semantic issues), part of
 * {@link EditorSettingsData}. Immutable; null values mean "default" until {@link #validated()}.
 *
 * @param showSemanticWarnings   the Semantic issues table also lists warnings
 * @param parsingErrorsAllFiles  the Parsing errors table lists the errors of every file of the project
 * @param semanticIssuesAllFiles the Semantic issues table lists the issues of every file of the project
 * @param showCode               both tables show the Code column
 */
public record ProblemsSettingsData(Boolean showSemanticWarnings, Boolean parsingErrorsAllFiles,
                                   Boolean semanticIssuesAllFiles, Boolean showCode) {

    public static ProblemsSettingsData defaults() {
        return new ProblemsSettingsData(true, false, false, false);
    }

    /** Copy where missing values are replaced by the defaults. */
    public ProblemsSettingsData validated() {
        ProblemsSettingsData d = defaults();
        return new ProblemsSettingsData(
            showSemanticWarnings == null ? d.showSemanticWarnings : showSemanticWarnings,
            parsingErrorsAllFiles == null ? d.parsingErrorsAllFiles : parsingErrorsAllFiles,
            semanticIssuesAllFiles == null ? d.semanticIssuesAllFiles : semanticIssuesAllFiles,
            showCode == null ? d.showCode : showCode);
    }

    public ProblemsSettingsData withShowSemanticWarnings(boolean value) {
        return new ProblemsSettingsData(value, parsingErrorsAllFiles, semanticIssuesAllFiles, showCode);
    }

    public ProblemsSettingsData withParsingErrorsAllFiles(boolean value) {
        return new ProblemsSettingsData(showSemanticWarnings, value, semanticIssuesAllFiles, showCode);
    }

    public ProblemsSettingsData withSemanticIssuesAllFiles(boolean value) {
        return new ProblemsSettingsData(showSemanticWarnings, parsingErrorsAllFiles, value, showCode);
    }

    public ProblemsSettingsData withShowCode(boolean value) {
        return new ProblemsSettingsData(showSemanticWarnings, parsingErrorsAllFiles, semanticIssuesAllFiles, value);
    }
}
