# Getting started

# Using a local OpenAI compatible endpoint 

Download and install [Ollama](https://ollama.com/) 

# Start Ollama 

1. Pull the Granite 8b Code model: 

```shell
ollama pull granite-code:8b
```

2. Launch the server: 
3. 
```shell
OLLAMA_FLASH_ATTENTION=1 OLLAMA_HOST=localhost:8000 ollama serve
```

# Running as Camel JBang Plugin

*NOTE*: not working due to https://issues.apache.org/jira/browse/CAMEL-20923. Use standalone mode for now.

## Install the plugin

NOTE: this requires Camel 4.7.0-SNAPSHOT locally with the [CAMEL-20917](https://github.com/apache/camel/pull/14640) patch applied.

Build and install

```shell
mvn install
```

Add to Camel JBang Plugins

```shell
jbang -Dcamel.jbang.version=4.7.0-SNAPSHOT camel@apache/camel plugin add -g org.apache.camel.jbang.ai -a camel-jbang-plugin-explain -v 1.0.0-SNAPSHOT -d "Explain things using AI"  explain`
```

# Running 

```shell
jbang -Dcamel.jbang.version=4.7.0-SNAPSHOT camel@apache/camel explain --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel"  "How can I create a Camel route?"
```

# Running as Standalone 

0. Build the package as standalone

```shell
mvn -Pstandalone package
```

1. Launch the Qdrant container

```shell
podman run -d --rm --name qdrant -p 6334:6334 -p 6333:6333 qdrant/qdrant:v1.7.4-unprivileged
```

2. Load data into the DB
```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-SNAPSHOT-jar-with-dependencies.jar load
```

3. Ask questions

```shell
java -jar target/camel-jbang-plugin-explain-4.7.0-SNAPSHOT-jar-with-dependencies.jar whatis --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "How can I enable manual commits for the Kafka component?"

java -jar target/camel-jbang-plugin-explain-4.7.0-SNAPSHOT-jar-with-dependencies.jar whatis --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "Is load balance enabled by default in the MongoDB component?"

java -jar target/camel-jbang-plugin-explain-4.7.0-SNAPSHOT-jar-with-dependencies.jar whatis --model-name=granite-code:8b --system-prompt="You are a coding assistant specialized in Apache Camel" "Is the client ID required for JMS 2.0 for the JMS component?"
```

4. Stop the DB

```shell
podman stop qdrant
```
