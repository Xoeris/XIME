package xime.persistence.peroom;
import java.lang.annotation.*;
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeUpdate {
    int onConflict() default ConflictStrategy.ABORT;
}
