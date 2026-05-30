package fr.cnrs.lacito.matchboxgrammarapi;

import java.nio.file.Path;

public abstract class AbstractGrammarPlugin implements GrammarPlugin {

    abstract public String getId();

    abstract public String getDisplayName();

    abstract public String getDisplayDescription();

    abstract public ParseResult getResult(Path filePath, String content);

}
