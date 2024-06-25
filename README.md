# Getting started

# Using a local OpenAI compatible endpoint 

Download and install [Ollama](https://ollama.com/) 

# Install the plugin

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
