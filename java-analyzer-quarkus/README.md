# Instructions to analyze a java application and transform it

TODO: To be rephrased as we have expanded the scope !

This project demonstrates how to launch the Eclipse Java Language Server (aka: Eclipse jdt-ls) and access it using a Java Language client able to pass commands and get JSON responses converted to objects

The project uses the [Spring TODO](../applications/spring-boot-todo-app) application as the project to be analyzed using [rules](rules).

## Setup

First, compile the project

```shell
./mvnw clean package
```

## Konveyor jdt-ls

Download the [konveyor language server](https://github.com/konveyor/java-analyzer-bundle) using the following image:
```shell
set VERSION latest

set ID $(podman create --name kantra-download quay.io/konveyor/kantra:$VERSION)
podman cp $ID:/jdtls ./jdt/konveyor-jdtls
```

**optional**: Copy the `konveyor-jdtls/java-analyzer-bundle/java-analyzer-bundle.core/target/java-analyzer-bundle.core-1.0.0-SNAPSHOT.jarjava-analyzer-bundle.core-1.0.0-SNAPSHOT.jar` to the `./lib/` folder of this project to use it as dependency (to access the code) as it is not published on a maven repository server !

### Trick to path the eclipse osgi server

Here is the trick to do to add a bundle to the OSGI jdt-ls server. This step is optional as we will pass the bundle path as initialization parameter to the language server !

Edit the `config.ini` file corresponding to your architecture: mac, linux, mac_arm under the folder konveyor-jdtls/config_<ARCH>

Modify within the config.ini file the `osgi.bundles` property and include after the `org.apache.commons.lang3...` jar the BundleSymbolicName of: java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar
```text
osgi.bundles=...org.apache.commons.lang3_3.14.0.jar@4,reference\:file\:java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar@2,...
```

Copy the java-analyzer-bundle.core-1.0.0-SNAPSHOT.jar file from the path `konveyor-jdtls/java-analyzer-bundle/java-analyzer-bundle.core/target/` to the `plugins` folder

## Download jdt-ls

Alternatively, you can also download the [Eclipse JDT Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls):

```shell
wget https://www.eclipse.org/downloads/download.php?file=/jdtls/milestones/1.50.0/jdt-language-server-1.50.0-202509041425.tar.gz > jdt-language-server-1.50.0.tar.gz
mkdir jdt-ls
tar -vxf jdt-language-server-1.50.0.tar.gz -C jdt-ls
```

## Start the language server and client using Quarkus CLI

To execute a command (analyze, etc) using the Quarkus Picocli CLI, execute this command 
```shell
Usage: java-analyzer analyze [-v] [--jdt-ls-path=<jdtLsPath>]
                             [--jdt-workspace=<jdtWorkspace>] [-r=<rulesPath>]
                             <appPath>
Analyze a project for migration
      <appPath>             Path to the Java project to analyze
      --jdt-ls-path=<jdtLsPath>
                            Path to JDT-LS installation (default: from config)
      --jdt-workspace=<jdtWorkspace>
                            Path to JDT workspace directory (default: from
                              config)
  -r, --rules=<rulesPath>   Path to rules directory (default: from config)
  -v, --verbose             Enable verbose output
  
...  

mvn quarkus:dev -Dquarkus.args="analyze --jdt-ls-path /PATH/TO/java-analyzer-quarkus/jdt/konveyor-jdtls --jdt-workspace /PATH/TO/java-analyzer-quarkus/jdt -r /PATH/TO/java-analyzer-quarkus/rules ./applications/spring-boot-todo-app"
```

To avoid to pass the arguments to the command, you can use the "default" [application.properties](src/main/resources/application.properties) and just pass the path of the application to be analyzed

```shell
mvn quarkus:dev -Dquarkus.args="analyze ./applications/spring-boot-todo-app"
```

The command can also be executed using the jar file
```shell
java -jar target/quarkus-app/quarkus-run.jar analyze ./applications/spring-boot-todo-app
```

You can check the log of the server from the parent folder within: `.jdt_workspace/.metadata/.log` !

## Transform your application

```shell
mvn quarkus:dev -Dquarkus.args="transform ./applications/spring-boot-todo-app"
```

## Start using the JdtlsFactory Main application

Before to run the server and client, configure the following system properties or override the Quarkus properties[application.properties](src/main/resources/application.properties):
- `JDT_WKS`: Path of the folder containing the jdt-ls workspace, .metadata and log. Default: `./jdt/`
- `JDT_LS_PATH`: Path of the jdt language server folder. Default: `./jdt/konveyor-jdtls`
- `LS_CMD`: Language server command to be executed. Default: `io.konveyor.tackle.ruleEntry`, etc
- `APP_PATH`: Path of the java project to analyze. Default: `./applications/spring-boot-todo-app`
- `RULES_PATH`: Path of the rules. Default: `./rules`

```shell
mvn exec:java
```
