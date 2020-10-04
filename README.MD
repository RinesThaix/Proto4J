# Proto4J

[![License](https://img.shields.io/github/license/RinesThaix/Proto4J)](/LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/rinesthaix/proto4j/badge)](https://www.codefactor.io/repository/github/rinesthaix/proto4j)

###### RPC and networking library for Java

## [Commons](./commons/)

Range of utilities for other modules. Includes [`LambdaReflection`](./commons/src/main/java/sexy/kostya/proto4j/commons/LambdaReflection.java) which allows to set and get field
values of an instance or access constructor via functional interfaces created by [`LambdaMetafactory`](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/LambdaMetafactory.html).

## [Serialization](./serialization/)

Abstract serialization library that supports any buffer implementation. Automatically (de-)serializes
classes marked with [`@AutoSerializable`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/annotation/AutoSerializable.java) consisting of fields of default types,
[`Proto4jSerializable`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/Proto4jSerializable.java) implementations or other [`@AutoSerializable`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/annotation/AutoSerializable.java) members.

Also supports inheritance: serializable class can extend other [`@AutoSerializable`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/annotation/AutoSerializable.java) in which case
all parent fields will also become transitively serialized.

Fields which should be ignored during serialization should be annotated with [`@Transient`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/annotation/Transient.java).

## [Transport](./transport/)

Networking library with custom UDP-based implementation with configurable reliability.

### Low level

[`Proto4jServer`](./transport/src/main/java/sexy/kostya/proto4j/transport/lowlevel/Proto4jServer.java) and [`Proto4jClient`](./transport/src/main/java/sexy/kostya/proto4j/transport/lowlevel/Proto4jClient.java) allow you to transfer data using datagrams between sockets.

By default, any transmitted data is ordered, reliable, is allowed to be split into several UDP packets
and be combined back on receiver side and is guaranteed to be delivered.

#### Packets structure

All UDP packets that are being sent are of the following structure:

1. **Header**
   1. **Length**: 2 bytes
   2. **Flags**: 1 byte
   3. **Sequence number**: 4 bytes
2. **Body**: *Variable length*
3. **Signature**: 4 bytes

#### Flags

It is your choice to select how to transmit the data. It can be configured by specifying flags for
sending methods. They are all located in [`Proto4jPacket`](./transport/src/main/java/sexy/kostya/proto4j/transport/packet/Proto4jPacket.java).`Flag`.

The following flags are available:

| Name            | Value  | Meaning                                                      |
| --------------- | ------ | ------------------------------------------------------------ |
| `CONFIRMATION`  | `0x01` | Marks that this packet is an indicator of other packets having been successfully received. Required for transmission reliability. In general, for internal usage only. |
| `PARTIAL`       | `0x02` | Marks that this exact UDP packet is part of a larger one. When used with `CONFIRMATION` flag together it indicates that some part of a larger packet has been delivered. |
| `UNORDERED`     | `0x04` | Marks that this packet can be handled out of order.          |
| `UNSIGNED_BODY` | `0x08` | By default, all sent packets are signed using [_CRC32_](https://en.wikipedia.org/wiki/Cyclic_redundancy_check), but for packets with that flag specified only the header of a packet will be signed. This means that packets may contain invalid bytes (although no data loss is still guaranteed). |
| `UNRELIABLE`    | `0x10` | Marks this packet as not requiring confirmation. In case of receiver not receiving this packet sender will do nothing about it. |
| `INDIVISIBLE`   | `0x20` | UDP packets are limited in length, so _Proto4J_ splits huge data into several smaller packets. This flag indicates that in case of the packet exceeding the single packet's size limit an exception will be thrown instead of performing splitting. |

#### Handlers

No handshaking or pinging is supported at this level but you can setup your own packet handlers
using [`Proto4jSocket`](/transport/src/main/java/sexy/kostya/proto4j/transport/lowlevel/Proto4jSocket.java)`.setInitialPacketHandler(BiConsumer<C, Proto4jPacket>)` method. Packets coming to this point are never marked
with `CONFIRMATION` or `PARTIAL` flags so all [`Proto4jPacket`](.transport/src/main/java/sexy/kostya/proto4j/transport/packet/Proto4jPacket.java) instances handled there contain exact data sent by the sender (up to the `UNSIGNED_BODY` flag).

Also, when you're starting the socket a [`CompletionStage<Void>`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) will be returned which may
help you in initiating the logic of communication between sockets.

#### Worker and handler threads

When you're about to instantiate any socket in _Proto4J_ you have to pass worker and handler
threads amount to the socket constructor.

_Workers_ are only used for reading data from the socket.

_Handlers_ are used for handling logic when a new packet appears.

### High level

This is a higher level interface over the previous level. To start working with it, have a look at [`Proto4jHighServer`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/Proto4jHighServer.java) and [`Proto4jHighClient`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/Proto4jHighClient.java) or their base implementations: [`BaseProto4jHighServer`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/BaseProto4jHighServer.java)
and [`BaseProto4jHighClient`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/BaseProto4jHighClient.java).

#### True connection

When client interacts with the server at first it initiates _handshaking_. After its completion server and
client will ping each other in order to ensure connection not being lost.

#### High level packets

In contrast to *Low level*, you can send high level packets across the network not only by manipulating raw bytes but also bu using complex entities. To do so, create your own class extending [`EnumeratedProto4jPacket`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/packet/EnumeratedProto4jPacket.java) or
[`CallbackProto4jPacket`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/packet/CallbackProto4jPacket.java). All you have to do in order to make it working is to implement `write(Buffer)` and
`read(Buffer)` methods and register your packet in [`PacketManager`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/packet/PacketManager.java) on both sides.

Also, there is an alternative [`PacketHandler`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/packet/PacketHandler.java) class which works with those packets instead of  [`Proto4jPacket`](.transport/src/main/java/sexy/kostya/proto4j/transport/packet/Proto4jPacket.java)s.

#### Callbacks

It is a common scenario to await for some packet responding to the sent ones. These functionality is
already implemented at this level. You can specify max awaiting time and handle response in the
way you want. This can be done by sending the initial packet using [`HighChannel`](./transport/src/main/java/sexy/kostya/proto4j/transport/highlevel/HighChannel.java).`sendWithCallback(CallbackProto4jPacket)` method.

### System properties

The following is a list of system properties which can be used to
affect the way modules behave internally. All of time values are specified in milliseconds.

| Name                             | Default value   | Description                                                  |
| -------------------------------- | --------------- | ------------------------------------------------------------ |
| `proto4j.maxDatagramSize`        | `508`           | Maximum allowed datagram size. Be aware that it counts the whole UDP packet size. |
| `proto4j.maxSequenceNumber`      | `2_000_000_000` | Maximum sequence number of the packet. When the internal counter reaches this value it will reset to zero. |
| `proto4j.reliabilityThreshold`   | `20`            | Delay of unconfirmed (and not marked with `UNRELIABLE` flag) packets. |
| `proto4j.callbacksRegistryDelay` | `100`           | Rate at which callbacks' registry checks retrieves its timed out callbacks. |
| `proto4j.callbacksInitialDelay`  | `500`           | It's the default time used whenever a packet is sent and awaited whenever awaiting time is not explicitly specified. |
| `proto4j.highTimeout`            | `10_000`        | If server doesn't receive any packets from client for that long it will disconnect the latter. |
| `proto4j.highPingDelay`          | `1_000`         | If server indicates that there were no receptions from or sendings to the client for that long it will send the response to the latter and await for a ping packet. |

## [RPC](./rpc/)

This is a higher-level API over *High level* one. Instead of manually implementing packets and their handling you work via services.

To start working with it use [`RpcServer`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/transport/RpcServer.java) and [`RpcClient`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/transport/RpcClient.java).

### Topology

Server at this level is only used for routing purposes but clients act both as service users and
implementors.

### Service

Service consist of interface and implementation parts. As a service user you can obtain service
interface instance via [`RpcClient`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/transport/RpcClient.java)`.getServiceManager().getService(Class<S>)`. All it's methods will be
proxied to registered implementations and will be executed remotely.

#### Interface rules

To create your own service start with an interface and annotate it with [@Proto4jService](./rpc/src/main/java/sexy/kostya/proto4j/rpc/service/annotation/Proto4jService.java).

Service interface is allowed to have default and static methods but their return type must be `void`,
serializable or a [`CompletionStage`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) of the previous types. Also, all arguments must be serializable.

Serializable types are the following:

- All primitives and their wrappers
- [`String`](https://docs.oracle.com/javase/8/docs/api/java/lang/String.html) and [`UUID`](https://docs.oracle.com/javase/8/docs/api/java/util/UUID.html)
- Classes annotated with [`@AutoSerializable`](./serialization/src/main/java/sexy/kostya/proto4j/serialization/annotation/AutoSerializable.java)
- Classes implementing [`BufferSerializable`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/BufferSerializable.java)
- [`List`](https://docs.oracle.com/javase/8/docs/api/java/util/List.html), [`Set`](https://docs.oracle.com/javase/8/docs/api/java/util/Set.html) and [`Map`](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html) of serializable types
- Arrays of serializable types

If a method should be executed on all registered service implementations it should be annotated with
[`@Broadcast`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/service/annotation/Broadcast.java) however such methods can only return `void` or [`CompletionStage<Void>`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html).

By default, when you invoke the method it will be executed on a random implementation.
If you want to control execution distribution, mark some of the method's arguments with [`@Index`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/service/annotation/Index.java): whenever
the method gets invoked implementation will be selected based on [hash code](https://docs.oracle.com/javase/7/docs/api/java/lang/Object.html#hashCode()) of marked arguments.

Whenever the service is registered all methods get converted to integer identifier. There can't be
two methods with the same identifier but such a situation may occur. To handle it, annotate the
method with [`@MethodIdentifier`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/service/annotation/MethodIdentifier.java) with explicitly specified static identifier.

#### Implementation

When you already created a service interface, now create it's implementation and register it
using [`RpcClient`](./rpc/src/main/java/sexy/kostya/proto4j/rpc/transport/RpcClient.java)`.getServiceManager().registerService(Class<S>, I)`.

Common scenario is having service interface on two sets of clients yet having the implementation
on only one of them.

### Conclave

This is a higher-level layer over basic [RPC](#RPC).

When creating a distributed back-end (i.e. microservices) it's a good practice to minimize the
number of points of failure. There's only one point of failure in the scheme described in the previous section which is
single server instance. _Conclave_ is a set of servers that work simultaneously.

All servers on _Conclave_ are connected to each other but every client is only connected to
a single server. RPC queries are being gracefully distributed and routed across the whole network
so that you don't have to worry about it.

To start working with _Conclave_, have a look at `RpcConclaveServer` and `RpcConclaveClient`.
To instantiate any of them, you will have to pass a [`List<InetSocketAddress>`](https://docs.oracle.com/javase/8/docs/api/java/util/List.html) - list of all
servers' destination points.

### System properties

As for the _Transport_ module, there is a set of system properties that are being looked for
in _RPC_ module.

| Name                       | Default value | Description                                                  |
| -------------------------- | ------------- | ------------------------------------------------------------ |
| `proto4j.conclaveWorkers`  | `2`           | Number of worker threads used by each of the server internal clients (which are being used to access other servers). |
| `proto4j.conclaveHandlers` | `2`           | Number of handler threads used by each of server internal clients (which are being used to access other servers). |
| `proto4j.conclaveTimeout`  | `1_000`       | Maximum time for which the server will wait until handshaking with other server is done. Otherwise it will consider the latter as a not-running one ending own attempts to connect in which case the connection will only be restarted in case of request from another one on its startup. |