# Instructions to play with Eclipse JDT-LS & the LS client

This project demonstrates how to launch the Eclipse Java Language Server (aka: Eclipse jdt-ls) and access it using a Java Language client able to pass commands and get JSON responses converted to objects

The project uses the [Spring TODO](../applications/spring-boot-todo-app) application as the project to be analyzed using [rules](rules).

## Setup

First, compile the project

```shell
./mvnw clean package
```

## Download jdt-ls

Download and unzip the Eclipse JDT Language Server:

```shell
wget https://www.eclipse.org/downloads/download.php?file=/jdtls/milestones/1.50.0/jdt-language-server-1.50.0-202509041425.tar.gz > jdt-language-server-1.50.0.tar.gz
mkdir jdt-ls
tar -vxf jdt-language-server-1.50.0.tar.gz -C jdt-ls
```

## Konveyor jdt-ls

Alternatively, you can also download and/or use your own server:
```shell
set VERSION latest

set ID $(podman create --name kantra-download quay.io/konveyor/kantra:$VERSION)
podman cp $ID:/jdtls ./konveyor-jdtls
```

**optional**: Copy the `konveyor-jdtls/java-analyzer-bundle/java-analyzer-bundle.core/target/java-analyzer-bundle.core-1.0.0-SNAPSHOT.jarjava-analyzer-bundle.core-1.0.0-SNAPSHOT.jar` to the `./lib/` of this project to allow maven to load it as it is not published on a maven repository server !

### Trick to path the eclipse osgi server

Here is the trick to do to add a bundle to the OSGI jdt-ls server. This step is optional as we will pass the bundle path as initialization parameter to the language server !

Edit the `config.ini` file corresponding to your architecture: mac, linux, mac_arm under the folder konveyor-jdtls/config_<ARCH>

Modify within the config.ini file the `osgi.bundles` property and include after the `org.apache.commons.lang3...` jar the BundleSymbolicName of: java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar
```text
osgi.bundles=...org.apache.commons.lang3_3.14.0.jar@4,reference\:file\:java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar@2,...
```

Copy the java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar file from the path `konveyor-jdtls/java-analyzer-bundle/java-analyzer-bundle.core/target/` to the `plugins` folder

## Start Jdt-ls and client

Before to run the server and client, configure the following properties:
- `JDT_WKS`: Path if the folder containing the jdt-ls workspace, .metadata and log
- `JDT_LS_PATH`: Path of the jdt-ls folder
- `LS_CMD`: Language server command to be executed. Example: `java.project.getAll`, etc
- `APP_PATH`: Path of the java project to analyze. Default: `./applications/spring-boot-todo-app`
- `RULES_PATH`: Path of the rules. Default: `./rules`

```shell
set -gx PARAMS "--jdt-ls-path /Users/cmoullia/code/application-modernisation/spring-to-quarkus-guide/lsp/jdt/konveyor-jdtls --jdt-workspace /Users/cmoullia/code/application-modernisation/spring-to-quarkus-guide/lsp/jdt -r /Users/cmoullia/code/application-modernisation/spring-to-quarkus-guide/java-analyzer-cli/rules"
mvn quarkus:dev -Dquarkus.args="analyze $PARAMS ./applications/spring-boot-todo-app"
```
You can check the log of the server from the parent folder within: `.jdt_workspace/.metadata/.log` !