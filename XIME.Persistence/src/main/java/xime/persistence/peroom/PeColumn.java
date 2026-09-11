package xime.persistence.peroom;
import java.lang.annotation.*;
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeColumn {
    String name() default "";
}
