package org.apache.camel.jbang.ai.util.steps;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.jbang.ai.util.handlers.BufferedStreamingResponseHandler;

/**
 * Control the chain-of-though interaction with the LLM API
 */
public class Steps {
    /**
     * A unit of conversation is its user message and the response from the LLM API
     */
    public static class ConversationUnit {
        private final ConversationUnit lastConversationUnit;
        private UserMessage userMessage;
        private String response;

        public ConversationUnit() {
            lastConversationUnit = null;
        }

        public ConversationUnit(ConversationUnit lastConversationUnit, UserMessage userMessage) {
            this.lastConversationUnit = lastConversationUnit;
            this.userMessage = userMessage;
        }

        public UserMessage userMessage() {
            return userMessage;
        }

        void setUserMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
        }

        public String response() {
            return response;
        }

        void setResponse(String response) {
            this.response = response;
        }

        public ConversationUnit lastConversationUnit() {
            return lastConversationUnit;
        }

        public ConversationUnit newConversationUnit() {
            return new ConversationUnit(this, null);
        }
    }

    /**
     * Holds the chat runtime metadata
     */
    public static class ChatMeta {
        private Object context;
        private ConversationUnit conversationUnit;
        private Exception exception;

        private ChatMeta() {
            conversationUnit = new ConversationUnit();
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
    private OpenAiStreamingChatModel chatModel;

    public static final class ChatStep {
        public Steps chat(Consumer<ChatMeta> consumer) {
            ChatMeta chatMeta = new ChatMeta();
            consumer.accept(chatMeta);

            return new Steps(chatMeta);
        }

        public Steps noContextChat() {
            return new Steps(new ChatMeta());
        }

        public static Steps using(OpenAiStreamingChatModel chatModel) {
            return new Steps(chatModel, new ChatMeta());
        }
    }

    public Steps(ChatMeta lastInputMeta) {
        this.chatMeta = lastInputMeta;
    }

    public Steps(OpenAiStreamingChatModel model, ChatMeta lastInputMeta) {
        this.chatModel = model;
        this.chatMeta = lastInputMeta;
    }

    /**
     * Sets a context to be shared through all the chat
     * @param consumer A consumer method that can set the context
     * @return
     */
    public Steps withContext(Consumer<ChatMeta> consumer) {
        consumer.accept(chatMeta);

        return this;
    }

    /**
     * The prompt to use for the conversation
     * @param consumer
     * @return
     */
    public Steps usingPrompt(Function<ChatMeta, UserMessage> consumer) {
        final UserMessage userMessage = consumer.apply(chatMeta);

        chatMeta.conversationUnit = chatMeta.conversationUnit.newConversationUnit();
        chatMeta.conversationUnit.setUserMessage(userMessage);
        return this;
    }

    /**
     * An optional method to execute after the chat with the LLM
     * @param consumer
     * @return
     */
    public Steps andThen(Consumer<ChatMeta> consumer) {
        consumer.accept(chatMeta);
        if (chatMeta.exception() != null) {
            // abort
        }

        return this;
    }

    /**
     * Calls the LLM API
     * @return
     */
    public Steps chat() {
        UserMessage userMessage = chatMeta.conversationUnit.userMessage;
        if (userMessage == null) {
            // Skipping this one

            return this;
        }

        CountDownLatch latch = new CountDownLatch(1);

        BufferedStreamingResponseHandler handler = new BufferedStreamingResponseHandler(latch);
        chatModel.generate(userMessage, handler);
        try {
            if (!latch.await(2, TimeUnit.MINUTES)) {
                System.out.println("Calling the LLM took too long, response might be incomplete.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            // call abort
            return this;
        }

        String response = handler.getResponse();
        chatMeta.conversationUnit.setResponse(response);
        return this;
    }

}
