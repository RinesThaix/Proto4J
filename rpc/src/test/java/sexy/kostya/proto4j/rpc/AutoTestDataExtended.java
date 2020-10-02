package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.serialization.annotation.AutoSerializable;

import java.util.UUID;

/**
 * Created by k.shandurenko on 03.10.2020
 */
@AutoSerializable
public class AutoTestDataExtended extends AutoTestData {

    private UUID uuid;

    public AutoTestDataExtended() {

    }

    public AutoTestDataExtended(String name, String password, int age, UUID uuid) {
        super(name, password, age);
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "AutoTestDataExtended{uuid=" + this.uuid + ", parent=" + super.toString() + '}';
    }
}
