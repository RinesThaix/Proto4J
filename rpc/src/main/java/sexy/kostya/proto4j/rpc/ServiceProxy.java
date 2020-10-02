package sexy.kostya.proto4j.rpc;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import sexy.kostya.proto4j.exception.Proto4jProxyingException;
import sexy.kostya.proto4j.rpc.annotation.MethodIdentifier;
import sexy.kostya.proto4j.rpc.annotation.Proto4jService;
import sexy.kostya.proto4j.rpc.serialization.SerializationMaster;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.buffer.BufferImpl;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public abstract class ServiceProxy {

    private final static int JAVA_VERSION = getJavaVersion();

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    private final Map<Integer, Map<Integer, Function<byte[], ListenableFuture<byte[]>>>> implementations = new HashMap<>();

    @SuppressWarnings("unchecked")
    public void registerService(Class<?> serviceInterface, Object implementation) {
        Class<?> clazz = implementation.getClass();
        if (!serviceInterface.isAssignableFrom(clazz)) {
            throw new Proto4jProxyingException(clazz.getSimpleName() + " does not inherit " + serviceInterface.getSimpleName());
        }
        if (!serviceInterface.isInterface()) {
            throw new Proto4jProxyingException(serviceInterface.getSimpleName() + " is not an interface");
        }
        if (!serviceInterface.isAnnotationPresent(Proto4jService.class)) {
            throw new Proto4jProxyingException(serviceInterface.getSimpleName() + " is not a Proto4jService");
        }
        Proto4jService annotation        = clazz.getAnnotation(Proto4jService.class);
        int            serviceIdentifier = annotation.explicitIdentifier() != 0 ? annotation.explicitIdentifier() : clazz.getSimpleName().hashCode();
        Map<Integer, Function<byte[], ListenableFuture<byte[]>>> methods = new HashMap<>();
        for (Method m : serviceInterface.getDeclaredMethods()) {
            if (m.isDefault()) {
                continue;
            }
            try {
                Method method = clazz.getDeclaredMethod(m.getName(), m.getParameterTypes());
                Class   returnType     = method.getReturnType();
                Class[] parameterTypes = method.getParameterTypes();
                int     methodIdentifier;
                if (method.isAnnotationPresent(MethodIdentifier.class)) {
                    MethodIdentifier methodAnnotation = method.getAnnotation(MethodIdentifier.class);
                    if (methodAnnotation.value() == 0) {
                        throw new Proto4jProxyingException("Identifier for " + clazz.getSimpleName() + "#" + method.getName() + " is zero, it's now allowed");
                    }
                    methodIdentifier = methodAnnotation.value();
                } else {
                    methodIdentifier = method.getName().hashCode();
                }
                Function[] readers = new Function[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; ++i) {
                    readers[i] = SerializationMaster.getReader(parameterTypes[i]);
                }
                Function<Object[], Object> invocation = getMethodInvocation(method);
                Function<byte[], ListenableFuture<byte[]>> func;
                if (returnType == void.class) {
                    func = bytes -> {
                        try (Buffer buffer = Buffer.wrap(bytes)) {
                            Object[] args = new Object[parameterTypes.length];
                            for (int i = 0; i < args.length; ++i) {
                                args[i] = readers[i].apply(buffer);
                            }
                            invocation.apply(args);
                            return null;
                        }
                    };
                } else if (ListenableFuture.class.isAssignableFrom(returnType)) {
                    returnType = (Class) returnType.getGenericInterfaces()[0];
                    BiConsumer<Buffer, Object> writer = SerializationMaster.getWriter(returnType);
                    func = bytes -> {
                        try (Buffer buffer = Buffer.wrap(bytes)) {
                            Object[] args = new Object[parameterTypes.length];
                            for (int i = 0; i < args.length; ++i) {
                                args[i] = readers[i].apply(buffer);
                            }
                            ListenableFuture future = (ListenableFuture) invocation.apply(args);
                            SettableFuture<byte[]> result = SettableFuture.create();
                            Futures.addCallback(future, new FutureCallback<Object>() {
                                @Override
                                public void onSuccess(@Nullable Object o) {
                                    try (Buffer buffer = Buffer.newBuffer()) {
                                        writer.accept(buffer, o);
                                        result.set(((BufferImpl) buffer).getHandle().array());
                                    }
                                }

                                @Override
                                public void onFailure(Throwable throwable) {
                                    result.setException(throwable);
                                }
                            }, getExecutor());
                            return result;
                        }
                    };
                } else {
                    BiConsumer<Buffer, Object> writer = SerializationMaster.getWriter(returnType);
                    func = bytes -> {
                        try (Buffer buffer = Buffer.wrap(bytes)) {
                            Object[] args = new Object[parameterTypes.length];
                            for (int i = 0; i < args.length; ++i) {
                                args[i] = readers[i].apply(buffer);
                            }
                            try (Buffer buffer2 = Buffer.newBuffer()) {
                                writer.accept(buffer2, invocation.apply(args));
                                SettableFuture<byte[]> result = SettableFuture.create();
                                result.set(((BufferImpl) buffer2).getHandle().array());
                                return result;
                            }
                        }
                    };
                }
                methods.put(methodIdentifier, func);
            } catch (NoSuchMethodException e) {
                throw new Proto4jProxyingException(clazz.getSimpleName() + "#" + m.getName() + " is not present");
            }
        }
        this.implementations.put(serviceIdentifier, methods);
    }

    public void process(HighChannel channel, RpcInvocationPacket packet) {
        Map<Integer, Function<byte[], ListenableFuture<byte[]>>> implementation = this.implementations.get(packet.getServiceID());
        if (implementation == null) {
            packet.respond(channel, new RpcResponsePacket("Unknown implementation error", null));
            return;
        }
        Function<byte[], ListenableFuture<byte[]>> method = implementation.get(packet.getMethodID());
        if (method == null) {
            packet.respond(channel, new RpcResponsePacket("Unknown method error", null));
            return;
        }
        ListenableFuture<byte[]> future = method.apply(packet.getArguments());
        if (future == null) {
            return;
        }
        Futures.addCallback(future, new FutureCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                packet.respond(channel, new RpcResponsePacket(null, bytes));
            }

            @Override
            public void onFailure(Throwable throwable) {
                packet.respond(channel, new RpcResponsePacket(throwable.getMessage(), null));
            }
        }, getExecutor());
    }

    @SuppressWarnings("unchecked")
    public <T> T proxy(Class<T> clazz) {
        if (!clazz.isInterface()) {
            throw new Proto4jProxyingException(clazz.getSimpleName() + " is not an interface");
        }

        if (!clazz.isAnnotationPresent(Proto4jService.class)) {
            throw new Proto4jProxyingException(clazz.getSimpleName() + " is not a Proto4jService");
        }
        Proto4jService annotation        = clazz.getAnnotation(Proto4jService.class);
        int            serviceIdentifier = annotation.explicitIdentifier() != 0 ? annotation.explicitIdentifier() : clazz.getSimpleName().hashCode();

        Map<Method, CheckedFunction<Object[], Object>> methods           = new ConcurrentHashMap<>();
        Set<Integer>                                   methodIdentifiers = new HashSet<>();

        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (proxy, passedMethod, passedArgs) -> {
            //noinspection CodeBlock2Expr
            return methods.computeIfAbsent(passedMethod, method -> {
                if (method.isDefault()) {
                    try {
                        MethodHandle handle = getTrustedHandleForMethod(clazz, method).bindTo(proxy);
                        return handle::invokeWithArguments;
                    } catch (Exception ex) {
                        throw new Proto4jProxyingException("Could not invoke default method", ex);
                    }
                }
                Class   returnType     = method.getReturnType();
                Class[] parameterTypes = method.getParameterTypes();
                int     methodIdentifier;
                if (method.isAnnotationPresent(MethodIdentifier.class)) {
                    MethodIdentifier methodAnnotation = method.getAnnotation(MethodIdentifier.class);
                    if (methodAnnotation.value() == 0) {
                        throw new Proto4jProxyingException("Identifier for " + clazz.getSimpleName() + "#" + method.getName() + " is zero, it's now allowed");
                    }
                    methodIdentifier = methodAnnotation.value();
                } else {
                    methodIdentifier = method.getName().hashCode();
                }
                if (!methodIdentifiers.add(methodIdentifier)) {
                    throw new Proto4jProxyingException("Identifier for " + clazz.getSimpleName() + "#" + method.getName() + " duplicates with identifier of another method");
                }
                BiConsumer[] writers = new BiConsumer[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; ++i) {
                    writers[i] = SerializationMaster.getWriter(parameterTypes[i]);
                }
                if (returnType == void.class) {
                    return args -> {
                        byte[]              arguments = serializeArguments(args, writers);
                        RpcInvocationPacket packet    = new RpcInvocationPacket(serviceIdentifier, methodIdentifier, arguments);
                        send(packet);
                        return null;
                    };
                } else if (ListenableFuture.class.isAssignableFrom(returnType)) {
                    Function<Buffer, Object> reader = SerializationMaster.getReader(returnType);
                    return args -> {
                        SettableFuture      future    = SettableFuture.create();
                        byte[]              arguments = serializeArguments(args, writers);
                        RpcInvocationPacket packet    = new RpcInvocationPacket(serviceIdentifier, methodIdentifier, arguments);

                        ListenableFuture<CallbackProto4jPacket> resultFuture = sendWithCallback(packet);
                        Futures.addCallback(resultFuture, new FutureCallback<CallbackProto4jPacket>() {
                            @Override
                            public void onSuccess(@Nullable CallbackProto4jPacket callbackProto4jPacket) {
                                RpcResponsePacket callback = (RpcResponsePacket) callbackProto4jPacket;
                                if (callback.getError() != null) {
                                    future.setException(new Exception(callback.getError()));
                                } else {
                                    try (Buffer buffer = Buffer.wrap(callback.getResponse())) {
                                        future.set(reader.apply(buffer));
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                future.setException(throwable);
                            }
                        }, getExecutor());
                        return future;
                    };
                } else {
                    Function<Buffer, Object> reader = SerializationMaster.getReader(returnType);
                    return args -> {
                        byte[]              arguments = serializeArguments(args, writers);
                        RpcInvocationPacket packet    = new RpcInvocationPacket(serviceIdentifier, methodIdentifier, arguments);

                        ListenableFuture<CallbackProto4jPacket> resultFuture = sendWithCallback(packet);
                        RpcResponsePacket                       callback     = (RpcResponsePacket) resultFuture.get();
                        if (callback.getError() != null) {
                            throw new Exception(callback.getError());
                        } else {
                            try (Buffer buffer = Buffer.wrap(callback.getResponse())) {
                                return reader.apply(buffer);
                            }
                        }
                    };
                }
            }).apply(passedArgs);
        });
    }

    public abstract Executor getExecutor();

    public abstract void send(RpcInvocationPacket packet);

    public abstract ListenableFuture<CallbackProto4jPacket> sendWithCallback(RpcInvocationPacket packet);

    @SuppressWarnings("unchecked")
    private byte[] serializeArguments(Object[] args, BiConsumer[] writers) {
        try (Buffer buffer = Buffer.newBuffer()) {
            for (int i = 0; i < args.length; ++i) {
                writers[i].accept(buffer, args[i]);
            }
            return ((BufferImpl) buffer).getHandle().array();
        }
    }

    private MethodHandle getTrustedHandleForMethod(Class<?> proxyClass, Method method) throws Exception {
        if (JAVA_VERSION < 8) {
            throw new Proto4jProxyingException("Proto4j RPC is not supported on java version lower than 8");
        }
        if (JAVA_VERSION == 8) {
            Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            constructor.setAccessible(true);
            return constructor.newInstance(proxyClass)
                    .in(proxyClass)
                    .unreflectSpecial(method, proxyClass);
        }
        return MethodHandles.lookup()
                .findSpecial(
                        proxyClass,
                        method.getName(),
                        MethodType.methodType(void.class, new Class[0]),
                        proxyClass
                );
    }

    private Function<Object[], Object> getMethodInvocation(Method method) {
        return null;
    }

    @FunctionalInterface
    private interface CheckedFunction<I, O> {
        O apply(I in) throws Throwable;
    }

}
