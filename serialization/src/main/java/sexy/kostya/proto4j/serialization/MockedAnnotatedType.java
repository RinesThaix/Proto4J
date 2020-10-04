package sexy.kostya.proto4j.serialization;

import sexy.kostya.proto4j.serialization.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;

/**
 * Created by k.shandurenko on 05.10.2020
 */
public class MockedAnnotatedType implements AnnotatedType {

    private final static Nullable ANNOTATION = new Nullable() {
        @Override
        public Class<Nullable> annotationType() {
            return Nullable.class;
        }
    };

    private final Type    type;
    private final boolean nullable;

    MockedAnnotatedType(Type type, boolean nullable) {
        this.type = type;
        this.nullable = nullable;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (!this.nullable || annotationClass != Nullable.class) {
            return null;
        }
        return (T) ANNOTATION;
    }

    @Override
    public Annotation[] getAnnotations() {
        if (!this.nullable) {
            return new Annotation[0];
        }
        return new Annotation[]{ANNOTATION};
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }
}
