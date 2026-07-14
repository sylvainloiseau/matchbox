package fr.cnrs.lacito.matchbox.language.corpusObject;

import java.nio.file.Path;

public record TextCoordinate(Path path, int startIndex, int stopIndex, int startLine, int endLine) {}
