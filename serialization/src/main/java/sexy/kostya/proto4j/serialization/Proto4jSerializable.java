package sexy.kostya.proto4j.serialization;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public interface Proto4jSerializable<B> {

    void write(B buffer);

    void read(B buffer);

}
