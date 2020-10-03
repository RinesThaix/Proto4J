package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.transport.buffer.Buffer;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class TestData implements BufferSerializable {

    private String name;
    private String password;
    private int    age;

    public TestData() {

    }

    public TestData(String name, String password, int age) {
        this.name = name;
        this.password = password;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public int getAge() {
        return age;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeString(this.name);
        buffer.writeString(this.password);
        buffer.writeVarInt(this.age);
    }

    @Override
    public void read(Buffer buffer) {
        this.name = buffer.readString();
        this.password = buffer.readString();
        this.age = buffer.readVarInt();
    }
}
