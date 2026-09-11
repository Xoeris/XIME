package xime.persistence.peroom;
import java.lang.annotation.*;
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeQuery {
    String value();
}
