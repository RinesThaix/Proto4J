# Proto4J

[![License](https://img.shields.io/github/license/RinesThaix/Proto4J)](/LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/rinesthaix/proto4j/badge)](https://www.codefactor.io/repository/github/rinesthaix/proto4j)

###### RPC and networking library for Java

## Commons

Range of utilities for other modules. Includes `LambdaReflection`, which allows to set and get field
values of an instance or access constructor via functional interfaces created by LambdaMetafactory.

## Serialization

Abstract serialization library that supports any buffer implementation. Automatically (de)serializes
classes marked with `@AutoSerializable`, which consist of fields of default types,
`Proto4jSerializable` implementations or other `@AutoSerializable` instances.

Also supports inheritance: serializable class can extend other `@AutoSerializable`. In this case,
all parent fields will also be serialized.

Whether you choose not to serialize some field, mark it with `@Transient`.

## Transport

Networking library with custom reliable UDP implementation.

### Low level

`Proto4jServer` and `Proto4jClient` allows you to transfer data using datagrams between sockets.

By default, any transmitted data is ordered, reliable, can be split into several UDP packets
and combined back on receiver side, and is guaranteed to be delivered.

#### Packets structure

All UDP packets that are being sent is of the following structure:
- Header
  - Packet length = 2 bytes
  - Packet flags = 1 byte
  - Sequence number = 4 bytes
- Body = variable length
- Signature = 4 bytes

#### Flags

It is your choice to select how to transmit the data. You can do so by specifying flags for
sending methods. They are all located in `Proto4jPacket.Flag`.

- `CONFIRMATION` | `0x01` | Marks that this packet is an indication that other packet was
successfully received. Required for transmission reliability. Probably for internal usage only.
- `PARTIAL` | `0x02` | Marks that this exact UDP packet is a part of a larger one. When used
with `CONFIRMATION` flag together, indicates that some part of a larger packet was delivered.
- `UNORDERED` | `0x04` | Marks that this packet can be handled out of order.
- `UNSIGNED_BODY` | `0x08` | By default, all sent packets are signed using _CRC32_, but for
packets with that flag specified only the header of a packet will be signed. So, any body
data can contain invalid bytes (but without losses).
- `UNRELIABLE` | `0x10` | Use this to explicitly mark that packet doesn't require confirmation.
In case receiver not received this, sender will do nothing about it.
- `INDIVISIBLE` | `0x20` | UDP packets are limited in length, so _Proto4J_ is splitting huge
data into several UDP packets. Whether you decide not to do so, specify this flag. If resulting
packet length will be larger than maximum allowed, exception will be thrown.

#### Handlers

At this level no handshaking or pinging is supported, but you can setup your own packet handlers
using `Proto4jSocket#setInitialPacketHandler` method. Packets coming to that point never marked
with `CONFIRMATION` or `PARTIAL` flags, so all `Proto4jPacket` instances you are going to
handle there contain exact data that were sent by the sender (up to the `UNSIGNED_BODY` flag).

Also, when you're starting the socket, a `CompletionStage<Void>` will be returned, what can
help you initiate the logic of communication between sockets.

#### Worker and handler threads

When you're about to instantiate any socket in _Proto4J_, you have to pass worker and handler
threads amount to the socket constructor.

_Workers_ are only used for reading data from the socket.

_Handlers_ are used for handling logic when a new packet appears.

### High level

This is an addon above the previous level. To start working with it, have a look at
`Proto4jHighServer` and `Proto4jHighClient` or their base implementations: `BaseProto4jHighServer`
and `BaseProto4jHighClient`.

#### True connection

When client contacts server at first, it initiates _handshaking_. After it's done, server and
client will ping each other to ensure connection isn't lost.

#### High level packets

In comparison to *Low level*, you can send across the network not only bytes, but also more
complex entities. To do so, create your own class extending `EnumeratedProto4jPacket` or
`CallbackProto4jPacket`. All you have to make it working is implement `write(Buffer)` and
`read(Buffer)` methods and register your packet in `PacketManager` of both sides.

Also, there is new `PacketHandler` class, that works with those packets (and not `Proto4jPacket` ones).

#### Callbacks

It is a common use case to await some packet in response to a sent one. These functionality is
already implemented at this level. You can specify max awaiting time and handle response the
way you want. This can be done by sending initial packet using `HighChannel#sendWithCallback` method.

### System properties

The following is a list of system properties with their default values you can specify to
affect how module works internally. All of time values are in milliseconds.

- `proto4j.maxDatagramSize` | `508` by default | Maximum allowed datagram size. Be aware that
it's not maximum body size, but the limitation for the whole UDP packet.
- `proto4j.maxSequenceNumber` | `2_000_000_000` by default | Maximum sequence number of the packet.
When counter reaches this limit, it will reset to zero.
- `proto4j.reliabilityThreshold` | `20` by default | Delay of unconfirmed (and not marked with
`UNRELIABLE` flag) packets.
- `proto4j.callbacksRegistryDelay` | `100` by default | Rate at which callbacks registry checks
whether some of callbacks are already timed out.
- `proto4j.callbacksInitialDelay` | `500` by default | It's the default time used when you
send a packet and await for it's callback without exactly awaiting time specified.
- `proto4j.highTimeout` | `10_000` by default | If server doesn't receive any packet from the
client for that long, it will disconnect the client.
- `proto4j.highPingDelay` | `1_000` by default | If server indicates there were no receptions
from or sendings to the client for that long, it will send and await in response a ping packet.

## RPC

This is also an addition, but for the *High level*. You don't have to create your own packets,
(de)serialize and handle them. Everything there is working via services.

To start working with it, use `RpcServer` and `RpcClient`.

### Topology

Server at this level is for routing purposes only, but clients act both as service users and
implementors.

### Service

Service consist of interface and implementation parts. As a service user, you can obtain service
interface instance from `RpcClient#getServiceManager#getService`. All it's methods will be
proxied to registered implementations and will be executed remotely.

#### Interface rules

To create your own service, start with an interface and mark it with `@Proto4jService`
annotation.

Service interface is allowed to have default/static methods, but return type must be a void,
serializable or a `CompletionStage` of such a type. Also, all arguments must be serializable.
Serializable types are the following:

- All primitives and their wrappers
- `String` and `UUID`
- Classes marked with `@AutoSerializable` annotation
- Classes implementing `BufferSerializable`
- `List`, `Set` and `Map` of serializable types
- Arrays of serializable types

If you want method to be executed on all service implementations registered, mark it with
`@Broadcast`, but be aware: such methods can return only a void on a `CompletionStage<Void>`.

By default, when you invoke the method, it will be executed on a random implementation.
If you want to control execution distribution, mark some of arguments with `@Index`: every time
you will invoke this method, implementation will be selected based on hashcode of marked arguments.

When service is being registered, all methods convert to integer identifier. There couldn't be
two methods with the same identifier, but such a situation can occur. To handle it, mark the
method with `@MethodIdentifier` annotation with exactly specified static identifier.

#### Implementation

When you already created a service interface, now create it's implementation and register it
using `RpcClient#getServiceManager#registerService`.

Common use case is to have service interface both on two sets of clients, but the implementation
is only on one of them.

### Conclave

It is another one addon, now - for basic _RPC_.

When creating a distributed backend (i.e. microservices), it's a good practice to minimize the
number of points of failure. In the scheme described above, there's only one point of failure -
single server instance. _Conclave_ is a set of servers that work simultaneously.

All servers on _Conclave_ are connected to each other, but every client is connected just to
a single server. RPC queries are being gracefully distributed and routed across the whole network
and you don't have to worry about it.

To start working with a _Conclave_, have a look at `RpcConclaveServer` and `RpcConclaveClient`.
To instantiate any of them, you will have to pass a `List<InetSocketAddress>` - list of all
servers destination points.

### System properties

As for the _Transport_ module, there is a set of system properties that are being looked for
in _RPC_ module.

- `proto4j.conclaveWorkers` | `2` by default | Number of worker threads for each of server
internal clients (which are being used to access other servers).
- `proto4j.conclaveHandlers` | `2` by default | Number of handler threads for each of server
internal clients (which are being used to access other servers).
- `proto4j.conclaveTimeout` | `1_000` by default | Maximum time for which server will wait until
handshaking with other server is done. Otherwise it will handle it as a not-running server,
will stop it's attempts to connect and will wait connection from another side at a time when
that server will start.