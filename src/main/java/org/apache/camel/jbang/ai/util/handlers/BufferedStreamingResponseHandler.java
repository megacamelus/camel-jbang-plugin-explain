package org.apache.camel.jbang.ai.util.handlers;

import java.util.concurrent.CountDownLatch;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;

public class BufferedStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {
    private final CountDownLatch latch;
    private StringBuffer responseBuffer = new StringBuffer();

    public BufferedStreamingResponseHandler(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void onNext(String s) {
        responseBuffer.append(s);
    }

    @Override
    public void onError(Throwable throwable) {
        latch.countDown();
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        try {
            StreamingResponseHandler.super.onComplete(response);
        } finally {
            latch.countDown();
        }
    }

    public String getResponse() {
        return responseBuffer.toString();
    }
}
