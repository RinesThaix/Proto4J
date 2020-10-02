package sexy.kostya.proto4j.rpc.service;

import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;

import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public interface InternalServiceManager extends ServiceManager {

    CompletionStage<RpcResponsePacket> invoke(RpcInvocationPacket packet);

}
