package xime.persistence.peroom;
import java.lang.annotation.*;
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeInsert {
    int onConflict() default ConflictStrategy.ABORT;
}
