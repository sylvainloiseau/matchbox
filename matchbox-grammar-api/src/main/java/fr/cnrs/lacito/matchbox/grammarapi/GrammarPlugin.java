package fr.cnrs.lacito.matchbox.grammarapi;

import java.nio.file.Path;

public interface GrammarPlugin {

    String getId();

    String getDisplayName();

    String getDisplayDescription();

    ParseResult getResult(Path filePath, String content);

}
    