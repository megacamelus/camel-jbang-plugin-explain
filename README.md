# Getting started

## Setting up the pre-requisites

In order to use this tool you need two things: an API endpoint and a Qdrant vector DB instance. 

## LLM API Endpoint 

In order to use this tool you need a LLM being served in a HTTP endpoint.

You can experiment with this tool in the open-source way using [Ollama](https://ollama.com/). It makes it easy to serve a model locally and works on all major operating systems. It also automatically tries to use your GPU for faster performance.

Once Ollama is installed following the instructions on their website, follow these steps: 

1. Start Ollama

    ```shell
    OLLAMA_FLASH_ATTENTION=1 OLLAMA_HOST=localhost:8000 ollama serve
    ```

    *NOTE*: If you use a different host make sure to pass it as an argument when using this tool (i.e., `--host=localhost` and `--port=11434`).

1. Pull the models: 

    First, pull the Granite Code Model. The [Granite 8b base](https://huggingface.co/ibm-granite/granite-8b-code-base) serves as the base model for this project.

    ```shell
    OLLAMA_HOST=localhost:8000 ollama pull granite-code:8b
    ```

    Then pull the Mistral model:
    ```shell
    OLLAMA_HOST=localhost:8000 ollama pull mistral:latest
    ```

1. Import the customized settings for the Granite model

    These settings make the Granite model more conservative. 

    ```shell
    cd modelfiles/granite-code-jbang-8b
    OLLAMA_HOST=localhost:8000 ollama create granite-code-jbang:8b -f ./Modelfile
    ```

    Alternatively, if you have enough memory (32Gb or more) you can try the 20b one:

    ```shell
    ollama pull granite-code:20b
    cd modelfiles/granite-code-jbang-20b
    OLLAMA_HOST=localhost:8000 ollama create granite-code-jbang:20b -f ./Modelfile
    ```

    Then, when using the application, pass the appropriate model name (i.e., `--model-name=granite-code-jbang:20b`).

## Vector DB: Qdrant

The Qdrant database is needed to load and persist embeddings. 

*NOTE*: If you are only using the `data` command then it is not needed.

```shell
podman run -d --rm --name qdrant -p 6334:6334 -p 6333:6333 qdrant/qdrant:v1.7.4-unprivileged
```

# Running

This tool works as a standalone application or as a JBang plugin.

## Running as Camel JBang Plugin

*NOTE*: **plugin mode is not working** due to https://issues.apache.org/jira/browse/CAMEL-20923. Use standalone mode for now.

### Install the plugin

NOTE: this requires Camel 4.7.0-SNAPSHOT or greater locally.

#### Build and install

1. Build

    ```shell
    mvn install
    ```

1. Add to Camel JBang Plugins

    ```shell
    jbang -Dcamel.jbang.version=4.7.0-SNAPSHOT camel@apache/camel plugin add -g org.apache.camel.jbang.ai -a camel-jbang-plugin-explain -v 1.0.0-SNAPSHOT -d "Explain things using AI"  explain`
    ```

1. Choose a command to run, such as:

    ```shell
    jbang -Dcamel.jbang.version=4.7.0-SNAPSHOT camel@apache/camel explain --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel"  "How can I create a Camel route?"
    ```

## Running as Standalone Application 

Build the package as standalone

```shell
mvn -Pstandalone package
```

# Usage Examples

Show all available commands:

```
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar --help
```

## Asking questions

First, make sure you have loaded data into the DB. You need to do this anytime you recreate the Vector DB

```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar load
```

Then, ask questions

```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar whatis --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "How can I enable manual commits for the Kafka component?"

java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar whatis --model-name=granite-code-jbang:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "Is load balance enabled by default in the MongoDB component?"

java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar whatis --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "Is the client ID required for JMS 2.0 for the JMS component?"
```

## Generate a training dataset

You can generate LLM training datasets from the catalog information.

JSON and Parquet files are generated in the `dataset` directory.

Generate training data using the component information:
```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar data generate --model-name --data-type components mistral:latest
```

Generate training data using the dataformat information:
```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar data generate --model-name --data-type dataformat mistral:latest
```

*NOTE*: A GPU is needed for this, otherwise it takes a very long time to generate the dataset (several days instead of about a day)

To upload the components dataset:

```shell
huggingface-cli upload --repo-type dataset my-org/camel-components .
```

To upload the data formats dataset:

```shell
huggingface-cli upload --repo-type dataset my-org/camel-dataformats .
```

## Generate the documentation dump for training dataset

Before you prepare your dataset, you need to install 2 tools: asciidoc and pandoc. It also assumes you have the Camel source 
code on your system.

.Linux installation
```shell
sudo dnf install -y asciidoc pandoc
```

.macOS installation
```shell
brew install asciidoc pandoc
```

Then, convert the documentation from Camel: 

```shell
scripts/prepare-docs-for-dataset.sh /path/to/your/camel/code/base
```

Dump the data:

```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar data dump --data-type component-documentation --source-path
```

## Generate the taxonomy for InstructLab 

To generate the taxonomy locally, follow these steps.

Download the taxonomy from https://github.com/megacamelus/taxonomy

Download the documentation repo from https://github.com/megacamelus/camel-upstream-info/tree/main. Then update the data using:

```shell
make fetch-docs fetch-components
```

Then, then run the following command to regenerate the taxonomy:

```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-jar-with-dependencies.jar generate taxonomy --author orpiske \
   --document-repo https://github.com/megacamelus/camel-upstream-info \
   --document-commit e83af34070dcb575c96329ae1d5a9620ff8b4899 \
   --document-path $HOME/code/other/camel-assistant-taxonomy/camel-upstream-info/camel-components
   --taxonomy-path $HOME/code/python/instruct-lab/taxonomy/knowledge/technical_manual/apache/camel/features/components
```

Note: 
* taxonomy-path: the path to the taxonomy used to train with InstructLab
* document-path: the path for the documents referenced in the taxonomy. InstructLab does not need those, but this application needs it to use to regenerate the QnA.

After that, you can run InstructLab training steps.