# matchbox -- an application for parsing fieldnote and building corpus as plain text

Write field notes in plain text with small convention and parse the file for extracting IGT text and lexicon entries without copying them.

## Installation

## Scenario

This app experiment a alternative workflow for the annotation of linguistic data (text, lexicon) using plain text.

The typical workflow with linguistic fieldnote is to take note following purely idiosyncratic conventions, and the to copy lexical pieces of information and texts into a dedicated application, such as Flex or Elan. In doing so :

- the other information written down in manuscript of digital notebook are not looked at again. Some data are lost, such as short sentences that are not full text;
- it is not easy to keep the track from a pieces of information into a lexical database and the notebook page it comes from;
- the copy process is tedious; lot of fielnote data stay unexploited due to the fact that the time is missing to copy everything.

The matchbox application expriment another workflow, that may be usefull in some situations. The idea is to write down information, in notebook, in plain text, using small conventions for delimiting interlinear gloss or lexical information, that make it possible to parse the text files and to automatically extract pieces of information that could be gathered as corpus, or as lexicon. The matchbox application offer several parsers for matching information according to various conventions.

For instance, suppose you write down your fieldnote in the following manner :

```
<mami> 'pig'
<jaflo> 'kanu'

:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::
```

you can further use matchbox to extract the lexical information and add them to a lift dictionary ; to extract text in a text database or to convert it into flextext (for ingestion by Fex), or elan, etc.

##

A set of parsers, matching various conventions, can be associated with you fieldnote files. For instance, it is possible to add markdown-like headings for organizing texts.

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


If your lexical information are more structured, you can use a more complete language for defining lexical information :

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
