package org.apache.camel.jbang.ai.util.steps;

import java.util.function.Consumer;

import dev.langchain4j.data.message.UserMessage;

public class Steps {
    public static class ConversationUnit {
        private final UserMessage userMessage;
        private final String response;

        public ConversationUnit(UserMessage userMessage, String response) {
            this.userMessage = userMessage;
            this.response = response;
        }

        public UserMessage userMessage() {
            return userMessage;
        }

        public String response() {
            return response;
        }
    }
    public static class ChatMeta {
        private Object context;
        private ConversationUnit conversationUnit;
        private Exception exception;

        private ChatMeta() {
        }

        public <T> T context(Class<T> payloadType) {
            return payloadType.cast(context);
        }

        public void setContext(Object context) {
            this.context = context;
        }

        public ConversationUnit conversationUnit() {
            return conversationUnit;
        }

        public void setConversationUnit(ConversationUnit conversationUnit) {
            this.conversationUnit = conversationUnit;
        }

        public Exception exception() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }
    private ChatMeta chatMeta;

    public static final class InitialStep {
        public static ChatStep start() {
            return new ChatStep();
        }
    }

    public static final class ChatStep {
        public Steps chat(Consumer<ChatMeta> consumer) {
            ChatMeta chatMeta = new ChatMeta();
            consumer.accept(chatMeta);

            return new Steps(chatMeta);
        }

        public Steps noContextChat() {
            return new Steps(new ChatMeta());
        }
    }

    public Steps(ChatMeta lastInputMeta) {
        this.chatMeta = lastInputMeta;
    }

    public Steps andThen(Consumer<ChatMeta> consumer) {

        consumer.accept(chatMeta);
        if (chatMeta.exception() != null) {
            // abort
        }

        return this;
    }

}
