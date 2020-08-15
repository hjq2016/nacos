/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.remote.rsocket;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerPushRequest;
import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.PushFuture;
import com.alibaba.nacos.core.utils.Loggers;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * connection of rsocket.
 *
 * @author liuzunfei
 * @version $Id: RsocketConnection.java, v 0.1 2020年08月06日 11:58 AM liuzunfei Exp $
 */
public class RsocketConnection extends Connection {
    
    RSocket clientSocket;
    
    public RsocketConnection(ConnectionMetaInfo metaInfo, RSocket clientSocket) {
        super(metaInfo);
        this.clientSocket = clientSocket;
    }
    
    @Override
    public boolean heartBeatExpire() {
        return false;
    }
    
    @Override
    public boolean sendRequest(ServerPushRequest request, long timeoutMills) throws Exception {
        Loggers.RPC_DIGEST.info("Rsocket sendRequest :" + request);
        Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
        Payload block = payloadMono.block(Duration.ofMillis(timeoutMills));
        return block == null;
    }
    
    @Override
    public void sendRequestNoAck(ServerPushRequest request) throws Exception {
        Loggers.RPC_DIGEST.info("Rsocket sendRequestNoAck :" + request);
        clientSocket.fireAndForget(RsocketUtils.convertRequestToPayload(request, new RequestMeta())).block();
    }
    
    @Override
    public PushFuture sendRequestWithFuture(ServerPushRequest request) throws Exception {
        Loggers.RPC_DIGEST.info("Rsocket sendRequestWithFuture :" + request);
        final Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
    
        PushFuture defaultPushFuture = new PushFuture() {
            
            @Override
            public boolean isDone() {
                return payloadMono.take(Duration.ofMillis(0L)) == null;
            }
            
            @Override
            public boolean get() throws TimeoutException, InterruptedException {
                return payloadMono.block() == null;
            }
            
            @Override
            public boolean get(long timeout) throws TimeoutException, InterruptedException {
                return payloadMono.block(Duration.ofMillis(timeout)) == null;
            }
        };
        return defaultPushFuture;
    }
    
    @Override
    public void sendRequestWithCallBack(ServerPushRequest request, PushCallBack callBack) throws Exception {
    
        Loggers.RPC_DIGEST.info("Rsocket sendRequestWithCallBack :" + request);
        System.out.println(new Date() + "1");
        Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
        payloadMono.subscribe(new Consumer<Payload>() {
    
            @Override
            public void accept(Payload payload) {
                Response response = RsocketUtils.parseResponseFromPayload(payload);
                System.out.println(new Date().toString() + response);
                if (response.isSuccess()) {
                    callBack.onSuccess();
                } else {
                    callBack.onFail(new NacosException(response.getErrorCode(), response.getMessage()));
                }
            }
    
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                callBack.onFail(new Exception(throwable));
            }
        });
        try {
            System.out.println(new Date() + "2");
            payloadMono.timeout(Duration.ofMillis(callBack.getTimeout()));
            System.out.println(new Date() + "3");
        
        } catch (Exception e) {
            System.out.println("Timeout:" + e.getMessage());
            callBack.onTimeout();
        }
    }
    
    @Override
    public void closeGrapcefully() {
        if (clientSocket != null && !clientSocket.isDisposed()) {
            clientSocket.dispose();
        }
    }
}