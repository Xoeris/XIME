package xime.persistence.peroom;
import java.lang.annotation.*;
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface PeIgnore {
}
