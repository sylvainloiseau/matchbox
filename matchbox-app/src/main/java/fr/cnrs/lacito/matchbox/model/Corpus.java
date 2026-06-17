package fr.cnrs.lacito.matchbox.model;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.language.corpusObject.CorpusObject;
import fr.cnrs.lacito.matchbox.language.corpusObject.SimpleIGT;
import fr.cnrs.lacito.matchbox.language.corpusObject.SimpleLexicalDefinition;

/**
 * Manages file operations
 */
public class Corpus {

    private ObservableList<CorpusObject> corpus;
    private ObservableList<ParseError> errors;

    public Corpus() {
        this.corpus = FXCollections.observableArrayList();
        this.errors = FXCollections.observableArrayList();
        // derive the passiveTextList from the parserMap
    }


    public ObservableList<CorpusObject> getCorpus() {
        return corpus;
    }

    public List<SimpleIGT> getIgtForFile(Path selectedFilePath) {
        return corpus.stream()
            .filter(obj -> obj instanceof SimpleIGT)
            .map(obj -> (SimpleIGT) obj)
            .filter(igt -> igt.getTextCoordinate().path().equals(selectedFilePath))
            .collect(Collectors.toList());
    }

    public List<SimpleLexicalDefinition> getLexicalDefinitionsForFile(Path selectedFilePath) {
        return corpus.stream()
            .filter(obj -> obj instanceof SimpleLexicalDefinition)
            .map(obj -> (SimpleLexicalDefinition) obj)
            .filter(lex -> lex.getTextCoordinate().path().equals(selectedFilePath))
            .collect(Collectors.toList());
    }


    public void removeCorpusObjectsForFile(Path selectedFilePath) {
        corpus.removeIf(obj -> obj.getTextCoordinate().path().equals(selectedFilePath));
    }
    
    public void addCorpusObjects(List<CorpusObject> corpusObjects) {
        // TODO Auto-generated method stub
        corpus.addAll(corpusObjects);
    }

    public void addErrors(List<ParseError> errors) {
        this.errors.addAll(errors);
    }

    public List<ParseError> getErrorsForFile(Path selectedFilePath) {
        return errors.stream()
            .filter(error -> error.getFile().equals(selectedFilePath))
            .collect(Collectors.toList());
    }    

    // public ObservableList<LexicalDefinition> getLexicalDefinitions() {
    //     return lexList;
    // }

    // public List<SimpleIGT> getIgts(Path selectedFilePath) {
    //     return parserMap.get(selectedFilePath).getIGTs();
    // }

    // public List<LexicalDefinition> getLexicalDefinitions(Path selectedFilePath) {
    //     return parserMap.get(selectedFilePath).getLexicalDefinitions();
    // }



}
