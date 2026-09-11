package xime.persistence.peroom;
import java.lang.annotation.*;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeDatabase {
    Class<?>[] entities();
    int version();
}
