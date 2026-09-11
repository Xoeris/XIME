package xime.media;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Playlist implements Serializable {
    private String name;
    private final List<Metadata> items = new ArrayList<>();

    public Playlist(String name) {
        this.name = name;
    }

    public void addItem(Metadata item) {
        items.add(item);
    }

    public void removeItems(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public void shuffle() {
        Collections.shuffle(items);
    }

    public List<Metadata> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int size() {
        return items.size();
    }
}

