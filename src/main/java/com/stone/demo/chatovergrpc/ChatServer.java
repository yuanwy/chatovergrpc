package com.stone.demo.chatovergrpc;

import com.google.protobuf.Timestamp;
import com.stone.demo.ChatMessage;
import com.stone.demo.ChatServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public class ChatServer {

    private static final Logger logger = Logger.getLogger(ChatServer.class.getName());

    private final int port;
    private final Server server;

    public ChatServer(int port) throws IOException {
        this(ServerBuilder.forPort(port),port);
    }

    /** Create a RouteGuide server using serverBuilder as a base and features as data. */
    public ChatServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        server = serverBuilder.addService(new ChatServerService())
                .build();
    }

    /** Start serving requests. */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                ChatServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws InterruptedException,IOException {

        ChatServer server = new ChatServer(8888);
        server.start();
        server.blockUntilShutdown();
    }

    /**
     * Our implementation of RouteGuide service.
     *
     * <p>See route_guide.proto for details of the methods.
     */
    private static class ChatServerService extends ChatServiceGrpc.ChatServiceImplBase {

        private static ConcurrentMap<String, StreamObserver<ChatMessage>> observers = new ConcurrentHashMap<>();

        //private static Set<StreamObserver<ChatMessage>> observers = ConcurrentHashMap.newKeySet();


        @Override
        public StreamObserver<ChatMessage> chat(final StreamObserver<ChatMessage> responseObserver) {

            return new StreamObserver<ChatMessage>() {
                @Override
                public void onNext(ChatMessage value) {

                    if(!observers.containsKey(value.getFrom())){
                        observers.put(value.getFrom(),responseObserver);
                    }

                    long millis = System.currentTimeMillis();
                    Timestamp ts = Timestamp.newBuilder().setSeconds(millis / 1000)
                            .setNanos((int) ((millis % 1000) * 1000000)).build();

                    String replyFrom = value.getTo();
                    String replyTo = value.getFrom();
                    String replyMsg = String.format("From %s - %s: %s", ChatServerService.TimeStampToString(ts), replyFrom, value.getMessage());

                    ChatMessage responseMsg = ChatMessage.newBuilder()
                            .setFrom(replyFrom)
                            .setTo(replyTo)
                            .setTimestamp(ts)
                            .setMessage(replyMsg).build();

                    if(observers.containsKey(value.getTo())){
                        observers.get(value.getTo()).onNext(responseMsg);
                    }

                }

                @Override
                public void onError(Throwable t) {

                }

                @Override
                public void onCompleted() {
                    //observers.remove(responseObserver);
                }

            };
        }

        /**
         * 10位int型的时间戳转换为String(yyyy-MM-dd HH:mm:ss)
         *
         * @param timestamp
         * @return
         */
        public static String TimeStampToString(Timestamp timestamp) {

            final long MILLIS_PER_SECOND = 1000;
            //int转long时，先进行转型再进行计算，否则会是计算结束后在转型
            String tsStr = "";
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {

                Date date = new Date(timestamp.getSeconds() * MILLIS_PER_SECOND);
                tsStr = dateFormat.format(date);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return tsStr;

        }
    }

}
