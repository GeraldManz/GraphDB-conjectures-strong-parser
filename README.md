# READ ME
This repository contains all the changes made to the libraries of the framework Eclipse RDF4J to implement the _strong form_ of Conjectures. More precisely, the targeted files are _TrigParser.java_ for the trig parser and _SyntaxTreeBuilder.java_ for the query parser.

## Setup
To correctly install and use the contents of this folder, you first need to retrieve the complete [repository](https://github.com/eclipse-rdf4j/rdf4j) containing the libraries for the framework Eclipse RDF4J. Next, you need to replace the folders in this repository with the framework ones. More precisely, in the paths _..\rdf4j-main\core\queryparser_ and _..\rdf4j-main\rdf4j-main\core\rio\trig_.

## Testing
In order to test the changes made to the framework it is necessary to follow the following steps:
1. Get inside the folder you want to test (_..\rdf4j-main\core\queryparser\sparql_ or _..\rdf4j-main\rdf4j-main\core\rio\trig_);
2. Run the command `mvn test`.
The tests that will be performed will be those contained in the corresponding folder _..\src\test_.

## Installation
In order to install the changes made to the framework it is necessary to follow the following steps:
1. Get inside the folder you want to test (_..\rdf4j-main\core\queryparser\sparql_ or _..\rdf4j-main\rdf4j-main\core\rio\trig_);
2. Run the command `mvn package`;
3. The newly created _.jar_ file will be located in the corresposnding folder _..\target_.
To use the files obtained, simply replace the equivalents located in the folder containing the libraries (in the case of GraphDB, the path will be _..\GraphDB Desktop\app\lib_).
