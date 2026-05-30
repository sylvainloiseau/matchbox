# matchbox -- an application for parsing fieldnote and building corpus as plain text

# scenario

Fieldnote are usually copied into a database of interlinearization (such as Flex) or 

# Run in dev mode

mvn -pl matchbox-gui javafx:run   

mvn -e -pl matchbox-distribution -am clean package  

mvn -pl matchbox-distribution org.apache.maven.plugins:maven-assembly-plugin:single

mvn -pl matchbox-distribution help:evaluate -Dexpression=project.build.directory

mvn -pl grammars/matchbox-grammar-notebook package
mvn -pl matchbox-gui -am clean package
mvn -pl matchbox-distribution package
bash build/assembly/matchbox-distribution-1.0.0-distribution/bin/matchbox