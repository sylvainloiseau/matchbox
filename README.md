# matchbox -- an application for parsing field notes and building a corpus from plain text

Write field notes in plain text with lightweight conventions and parse the files for extracting IGT texts, lexicon entries or other linguistic data without copying them manually into dedicated apps.

## Installation

## Scenario

This app experiments with an alternative way of working with linguistic notes (text, lexicon) using plain text.

The typical workflow with linguistic fieldnotes is to take notes -- following idiosyncratic conventions --, and later on to copy data (lexical description, texts) into dedicated applications, such as Flex or Elan. In doing so:

- the other pieces of information written down in (manuscript or digital) notebooks are not looked at again. Some data are lost because they do not fit well into the app, such as short sentences that are not full text;
- it is not easy to keep track of the relation between a piece of information in a lexical database and the notebook page it comes from;
- the copy process is tedious; a lot of fieldnote data stays unexploited because there is not enough time to copy everything.

The matchbox application experiments with another workflow, which may be useful in some situations. The idea is to write digital notebooks in plain text, using lightweight conventions for delimiting interlinear gloss or lexical information, that make it possible to parse the text files and to automatically extract pieces of information that could be gathered into a corpus and a lexicon. The matchbox application offers several small parsers for matching information according to various conventions.

For instance, if you write down your field notes using the following format:

```
<mami> 'pig'
<jaflo> 'kanu'

:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::
```

you can further use matchbox to extract the lexical information and add them to a LIFT dictionary; to extract text into a text database or to convert it into flextext (for ingestion by Flex), or Elan, etc. matchbox offers various ways to aggregate the information found into existing dictionaries or text databases.

## Existing parser

Parsers exist for the following conventions:

### Simple lexical declaration:

```
```

### Simple IGT

### Text title and reference

### Link to audio file

### Conll

## Create parser for new conventions

## 

A set of parsers, matching various conventions, can be associated with your fieldnote files. For instance, it is possible to add markdown-like headings for organizing texts.

```
# data session : lexicon
---
date:
type:text
---

<mami> 'pig'
<jaflo> 'kanu'

'''
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
'''
```


If your lexical information is more structured, you can use a more complete language for defining lexical information:

```
# Lexical data session : lexicon
---
date:
type:text
---

//mami/pig/d@en 'wild or domesticated pig'


:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::
```


## Mixing annotation

## Adding to existing database

if not already present

# Run in dev mode

mvn -pl matchbox-gui javafx:run   

mvn -e -pl matchbox-distribution -am clean package  
mvn -pl matchbox-distribution package
mvn -pl matchbox-distribution org.apache.maven.plugins:maven-assembly-plugin:single

mvn -pl matchbox-distribution help:evaluate -Dexpression=project.build.directory

mvn -pl grammars/matchbox-grammar-notebook package
mvn -pl matchbox-gui -am clean package

bash build/assembly/matchbox-distribution-1.0.0-distribution/bin/matchbox
