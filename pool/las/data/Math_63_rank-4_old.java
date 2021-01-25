/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServerProtocolHandler;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * @since 4.2
 */
@Immutable // provided injected dependencies are immutable
public class HttpAsyncServiceHandler implements NHttpServerProtocolHandler {

    static final String HTTP_EXCHANGE_STATE = "http.nio.http-exchange-state";

    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpResponseFactory responseFactory;
    private final HttpAsyncRequestHandlerResolver handlerResolver;
    private final HttpAsyncExpectationVerifier expectationVerifier;
    private final HttpParams params;

    public HttpAsyncServiceHandler(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final HttpParams params) {
        super();
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.responseFactory = responseFactory;
        this.handlerResolver = handlerResolver;
        this.expectationVerifier = expectationVerifier;
        this.params = params;
    }

    public HttpAsyncServiceHandler(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpParams params) {
        this(httpProcessor, connStrategy, new DefaultHttpResponseFactory(),
                handlerResolver, null, params);
    }

    public void connected(final NHttpServerConnection conn) {
        State state = new State();
        conn.getContext().setAttribute(HTTP_EXCHANGE_STATE, state);
    }

    public void closed(final NHttpServerConnection conn) {
        State state = getState(conn);
        if (state != null) {
            synchronized (state) {
                closeHandlers(state);
                Cancellable asyncProcess = state.getAsyncProcess();
                if (asyncProcess != null) {
                    asyncProcess.cancel();
                }
                state.reset();
            }
        }
    }

    public void exception(
            final NHttpServerConnection conn, final Exception cause) {
        State state = ensureNotNull(getState(conn));
        if (state != null) {
            synchronized (state) {
                closeHandlers(state, cause);
                if (cause instanceof HttpException) {
                    if (conn.isResponseSubmitted()
                            || state.getResponseState() != MessageState.READY) {
                        // There is not much that we can do if a response
                        // has already been submitted
                        closeConnection(conn);
                    } else {
                        HttpContext context = state.getContext();
                        HttpAsyncResponseProducer responseProducer = handleException(
                                cause, context);
                        state.setResponseProducer(responseProducer);
                        try {
                            HttpResponse response = responseProducer.generateResponse();
                            state.setResponse(response);
                            commitFinalResponse(conn, state);
                        } catch (Exception ex) {
                            shutdownConnection(conn);
                            closeHandlers(state);
                            state.reset();
                            if (ex instanceof RuntimeException) {
                                throw (RuntimeException) ex;
                            } else {
                                log(ex);
                            }
                        }
                    }
                } else {
                    shutdownConnection(conn);
                    state.reset();
                }
            }
        } else {
            shutdownConnection(conn);
            log(cause);
        }
    }

    public void requestReceived(
            final NHttpServerConnection conn) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        synchronized (state) {
            HttpRequest request = conn.getHttpRequest();
            HttpContext context = state.getContext();
            request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

            context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            this.httpProcessor.process(request, context);

            state.setRequest(request);
            HttpAsyncRequestHandler<Object> requestHandler = getRequestHandler(request);
            state.setRequestHandler(requestHandler);
            HttpAsyncRequestConsumer<Object> consumer = requestHandler.processRequest(request, context);
            state.setRequestConsumer(consumer);

            consumer.requestReceived(request);

            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                    state.setRequestState(MessageState.ACK_EXPECTED);
                    HttpResponse ack = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                            HttpStatus.SC_CONTINUE, context);
                    if (this.expectationVerifier != null) {
                        conn.suspendInput();
                        HttpAsyncServiceExchange httpex = new Exchange(
                                request, ack, state, conn);
                        Cancellable asyncProcess = this.expectationVerifier.verify(httpex, context);
                        state.setAsyncProcess(asyncProcess);
                    } else {
                        conn.submitResponse(ack);
                        state.setRequestState(MessageState.BODY_STREAM);
                    }
                } else {
                    state.setRequestState(MessageState.BODY_STREAM);
                }
            } else {
                // No request content is expected.
                // Process request right away
                conn.suspendInput();
                processRequest(conn, state);
            }
        }
    }

    public void inputReady(
            final NHttpServerConnection conn,
            final ContentDecoder decoder) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        synchronized (state) {
            HttpAsyncRequestConsumer<?> consumer = ensureNotNull(state.getRequestConsumer());
            consumer.consumeContent(decoder, conn);
            state.setRequestState(MessageState.BODY_STREAM);
            if (decoder.isCompleted()) {
                conn.suspendInput();
                processRequest(conn, state);
            }
        }
    }

    public void responseReady(
            final NHttpServerConnection conn) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        synchronized (state) {
            if (state.getResponse() != null) {
                return;
            }
            HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
            if (responseProducer == null) {
                return;
            }
            HttpContext context = state.getContext();
            HttpResponse response = responseProducer.generateResponse();
            int status = response.getStatusLine().getStatusCode();
            if (state.getRequestState() == MessageState.ACK_EXPECTED) {
                if (status == 100) {
                    try {
                        // Make sure 100 response has no entity
                        response.setEntity(null);
                        conn.requestInput();
                        state.setRequestState(MessageState.BODY_STREAM);
                        conn.submitResponse(response);
                        responseProducer.responseCompleted(context);
                    } finally {
                        state.setResponseProducer(null);
                        responseProducer.close();
                    }
                } else if (status >= 400) {
                    conn.resetInput();
                    state.setRequestState(MessageState.COMPLETED);
                    state.setResponse(response);
                    commitFinalResponse(conn, state);
                } else {
                    throw new HttpException("Invalid response: " + response.getStatusLine());
                }
            } else {
                if (status >= 200) {
                    state.setResponse(response);
                    commitFinalResponse(conn, state);
                } else {
                    throw new HttpException("Invalid response: " + response.getStatusLine());
                }
            }
        }
    }

    public void outputReady(
            final NHttpServerConnection conn,
            final ContentEncoder encoder) throws IOException {
        State state = ensureNotNull(getState(conn));
        synchronized (state) {
            HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
            HttpContext context = state.getContext();
            HttpResponse response = state.getResponse();

            responseProducer.produceContent(encoder, conn);
            state.setResponseState(MessageState.BODY_STREAM);
            if (encoder.isCompleted()) {
                responseProducer.responseCompleted(context);
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                } else {
                    conn.requestInput();
                }
                closeHandlers(state);
                state.reset();
            }
        }
    }

    public void timeout(final NHttpServerConnection conn) throws IOException {
        State state = getState(conn);
        if (state != null) {
            synchronized (state) {
                closeHandlers(state, new SocketTimeoutException());
            }
        }
        if (conn.getStatus() == NHttpConnection.ACTIVE) {
            conn.close();
            if (conn.getStatus() == NHttpConnection.CLOSING) {
                // Give the connection some grace time to
                // close itself nicely
                conn.setSocketTimeout(250);
            }
        } else {
            conn.shutdown();
        }
    }

    private State getState(final NHttpConnection conn) {
        return (State) conn.getContext().getAttribute(HTTP_EXCHANGE_STATE);
    }

    private State ensureNotNull(final State state) {
        if (state == null) {
            throw new IllegalStateException("HTTP exchange state is null");
        }
        return state;
    }

    private HttpAsyncRequestConsumer<Object> ensureNotNull(final HttpAsyncRequestConsumer<Object> requestConsumer) {
        if (requestConsumer == null) {
            throw new IllegalStateException("Request consumer is null");
        }
        return requestConsumer;
    }

    protected void log(final Exception ex) {
    }

    private void closeConnection(final NHttpConnection conn) {
        try {
            conn.close();
        } catch (IOException ex) {
            log(ex);
        }
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ex) {
            log(ex);
        }
    }

    private void closeHandlers(final State state, final Exception ex) {
        HttpAsyncRequestConsumer<Object> consumer = state.getRequestConsumer();
        if (consumer != null) {
            try {
                consumer.failed(ex);
            } finally {
                try {
                    consumer.close();
                } catch (IOException ioex) {
                    log(ioex);
                }
            }
        }
        HttpAsyncResponseProducer producer = state.getResponseProducer();
        if (producer != null) {
            try {
                producer.failed(ex);
            } finally {
                try {
                    producer.close();
                } catch (IOException ioex) {
                    log(ioex);
                }
            }
        }
    }

    private void closeHandlers(final State state) {
        HttpAsyncRequestConsumer<Object> consumer = state.getRequestConsumer();
        if (consumer != null) {
            try {
                consumer.close();
            } catch (IOException ioex) {
                log(ioex);
            }
        }
        HttpAsyncResponseProducer producer = state.getResponseProducer();
        if (producer != null) {
            try {
                producer.close();
            } catch (IOException ioex) {
                log(ioex);
            }
        }
    }

    protected HttpAsyncResponseProducer handleException(
            final Exception ex, final HttpContext context) {
        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        String message = ex.getMessage();
        if (message == null) {
            message = ex.toString();
        }
        HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                code, context);
        return new ErrorResponseProducer(response, NStringEntity.create(message), false);
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    private void processRequest(
            final NHttpServerConnection conn,
            final State state) throws HttpException, IOException {
        HttpAsyncRequestHandler<Object> handler = state.getRequestHandler();
        HttpContext context = state.getContext();
        HttpAsyncRequestConsumer<?> consumer = state.getRequestConsumer();
        consumer.requestCompleted(context);
        state.setRequestState(MessageState.COMPLETED);
        Exception exception = consumer.getException();
        if (exception != null) {
            HttpAsyncResponseProducer responseProducer = handleException(exception, context);
            state.setResponseProducer(responseProducer);
            conn.requestOutput();
        } else {
            HttpRequest request = state.getRequest();
            Object result = consumer.getResult();
            HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                    HttpStatus.SC_OK, context);
            HttpAsyncServiceExchange httpex = new Exchange(
                    request, response, state, conn);
            try {
                Cancellable asyncProcess = handler.handle(result, httpex, context);
                state.setAsyncProcess(asyncProcess);
            } catch (HttpException ex) {
                HttpAsyncResponseProducer responseProducer = handleException(ex, context);
                state.setResponseProducer(responseProducer);
                conn.requestOutput();
            }
        }
    }

    private void commitFinalResponse(
            final NHttpServerConnection conn,
            final State state) throws IOException, HttpException {
        HttpContext context = state.getContext();
        HttpRequest request = state.getRequest();
        HttpResponse response = state.getResponse();

        response.setParams(new DefaultedHttpParams(response.getParams(), this.params));
        context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        this.httpProcessor.process(response, context);

        HttpEntity entity = response.getEntity();
        if (entity != null && !canResponseHaveBody(request, response)) {
            response.setEntity(null);
            entity = null;
        }

        conn.submitResponse(response);

        if (entity == null) {
            HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
            responseProducer.responseCompleted(context);
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                // Ready to process new request
                conn.requestInput();
            }
            closeHandlers(state);
            state.reset();
        } else {
            state.setResponseState(MessageState.BODY_STREAM);
        }
    }

    @SuppressWarnings("unchecked")
    private HttpAsyncRequestHandler<Object> getRequestHandler(final HttpRequest request) {
        HttpAsyncRequestHandler<Object> handler = null;
        if (this.handlerResolver != null) {
            String requestURI = request.getRequestLine().getUri();
            handler = (HttpAsyncRequestHandler<Object>) this.handlerResolver.lookup(requestURI);
        }
        if (handler == null) {
            handler = new NullRequestHandler();
        }
        return handler;
    }

    static class State {

        private final BasicHttpContext context;
        private volatile HttpAsyncRequestHandler<Object> requestHandler;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpAsyncRequestConsumer<Object> requestConsumer;
        private volatile HttpAsyncResponseProducer responseProducer;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile Cancellable asyncProcess;

        State() {
            super();
            this.context = new BasicHttpContext();
            this.requestState = MessageState.READY;
            this.responseState = MessageState.READY;
        }

        public HttpContext getContext() {
            return this.context;
        }

        public HttpAsyncRequestHandler<Object> getRequestHandler() {
            return this.requestHandler;
        }

        public void setRequestHandler(final HttpAsyncRequestHandler<Object> requestHandler) {
            this.requestHandler = requestHandler;
        }

        public MessageState getRequestState() {
            return this.requestState;
        }

        public void setRequestState(final MessageState state) {
            this.requestState = state;
        }

        public MessageState getResponseState() {
            return this.responseState;
        }

        public void setResponseState(final MessageState state) {
            this.responseState = state;
        }

        public HttpAsyncRequestConsumer<Object> getRequestConsumer() {
            return this.requestConsumer;
        }

        public void setRequestConsumer(final HttpAsyncRequestConsumer<Object> requestConsumer) {
            this.requestConsumer = requestConsumer;
        }

        public HttpAsyncResponseProducer getResponseProducer() {
            return this.responseProducer;
        }

        public void setResponseProducer(final HttpAsyncResponseProducer responseProducer) {
            this.responseProducer = responseProducer;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public void setRequest(final HttpRequest request) {
            this.request = request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            this.response = response;
        }

        public Cancellable getAsyncProcess() {
            return this.asyncProcess;
        }

        public void setAsyncProcess(final Cancellable asyncProcess) {
            this.asyncProcess = asyncProcess;
        }

        public void reset() {
            this.responseState = MessageState.READY;
            this.requestState = MessageState.READY;
            this.requestHandler = null;
            this.requestConsumer = null;
            this.responseProducer = null;
            this.request = null;
            this.response = null;
            this.asyncProcess = null;
            this.context.clear();
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("request state: ");
            buf.append(this.requestState);
            buf.append("; request: ");
            if (this.request != null) {
                buf.append(this.request.getRequestLine());
            }
            buf.append("; response state: ");
            buf.append(this.responseState);
            buf.append("; response: ");
            if (this.response != null) {
                buf.append(this.response.getStatusLine());
            }
            buf.append(";");
            return buf.toString();
        }

    }

    static class Exchange implements HttpAsyncServiceExchange {

        private final HttpRequest request;
        private final HttpResponse response;
        private final State state;
        private final NHttpServerConnection conn;

        public Exchange(
                final HttpRequest request,
                final HttpResponse response,
                final State state,
                final NHttpServerConnection conn) {
            super();
            this.request = request;
            this.response = response;
            this.state = state;
            this.conn = conn;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void submitResponse(final HttpAsyncResponseProducer responseProducer) {
            if (responseProducer == null) {
                throw new IllegalArgumentException("Response producer may not be null");
            }
            synchronized (this.state) {
                if (this.state.getResponseProducer() != null) {
                    throw new IllegalStateException("Response already submitted");
                }
                this.state.setResponseProducer(responseProducer);
                this.conn.requestOutput();
            }
        }

        public void submitResponse() {
            submitResponse(new BasicAsyncResponseProducer(this.response));
        }

        public boolean isCompleted() {
            synchronized (this.state) {
                return this.state.getResponseProducer() != null;
            }
        }

    }

}
