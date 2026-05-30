package fr.cnrs.lacito.matchboxlanguage.corpusObject;

import java.nio.file.Path;

public record TextCoordinate(Path path, int startIndex, int stopIndex, int startLine, int endLine) {}
