package fr.cnrs.lacito.matchboxgrammarapi;

import java.nio.file.Path;

public interface GrammarPlugin {

    String getId();

    String getDisplayName();

    String getDisplayDescription();

    ParseResult getResult(Path filePath, String content);

}
    