package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.serialization.annotation.AutoSerializable;
import sexy.kostya.proto4j.serialization.annotation.Transient;

/**
 * Created by k.shandurenko on 02.10.2020
 */
@AutoSerializable
public class AutoTestData {

    private String name;

    @Transient
    private String password;

    private int age;

    public AutoTestData() {
    }

    public AutoTestData(String name, String password, int age) {
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
    public String toString() {
        return "AutoTestData{" +
                "name='" + name + '\'' +
                ", password='" + password + '\'' +
                ", age=" + age +
                '}';
    }
}
