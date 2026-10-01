package fr.cnrs.lacito.matchbox.grammarapi;

import java.nio.file.Path;
import java.util.List;

public abstract class AbstractGrammarPlugin implements GrammarPlugin {

    abstract public String getId();

    abstract public String getDisplayName();

    abstract public String getDisplayDescription();

    abstract public String getFormatDocumentation();

    abstract public List<GrammarExample> getExamples();

    abstract public String getGrammarSource();

    abstract public ParseResult getResult(Path filePath, String content);

    abstract public int getApiVersion();

}
