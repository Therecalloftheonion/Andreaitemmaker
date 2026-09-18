package com.andreaitemmaker.editor;

import com.andreaitemmaker.config.ContentLoader;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A map inside a document that the editor can read and modify through the same menus, whether it
 * lives at a YAML path ({@link SectionNode}) or inside a list element ({@link MapNode}).
 *
 * <p>This is what lets the mechanic/map/list editors be generic: the GUI talks to a node, never
 * to the YAML API, so nested structures are edited the same way at any depth.
 */
public interface EditorNode {

    /** Snapshot of key to value, for rendering. Nested maps are plain maps, never sections. */
    Map<String, Object> entries();

    /** Set a key. A null value removes it. */
    void put(String key, Object value);

    void remove(String key);

    /** A node for a nested map at {@code key} (created on demand). */
    EditorNode child(String key);

    /** The section of a document: values are read/written at a dotted YAML path. */
    final class SectionNode implements EditorNode {

        private final EditorDocument document;
        private final String path;

        public SectionNode(EditorDocument document, String path) {
            this.document = document;
            this.path = path == null ? "" : path;
        }

        private String key(String key) {
            return path.isEmpty() ? key : path + "." + key;
        }

        @Override
        public Map<String, Object> entries() {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : document.keys(path)) {
                Object value = document.get(key(key));
                out.put(key, plain(value));
            }
            return out;
        }

        @Override
        public void put(String key, Object value) {
            if (value == null) {
                remove(key);
                return;
            }
            if (value instanceof Map<?, ?> map) {
                // A raw map value saves fine but is NOT readable as a section afterwards (only
                // createSection produces a traversable section), so nested maps are written as a
                // real section plus its children, at every depth.
                document.remove(key(key));
                document.yaml().createSection(key(key));
                EditorNode child = child(key);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    child.put(String.valueOf(entry.getKey()), plain(entry.getValue()));
                }
                return;
            }
            document.set(key(key), plain(value));
        }

        @Override
        public void remove(String key) {
            document.remove(key(key));
        }

        @Override
        public EditorNode child(String key) {
            return new SectionNode(document, key(key));
        }

        /** Convert a configuration section into plain maps/lists so menus can render it. */
        static Object plain(Object value) {
            if (value instanceof ConfigurationSection section) {
                return ContentLoader.sectionToMap(section);
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.put(String.valueOf(entry.getKey()), plain(entry.getValue()));
                }
                return out;
            }
            if (value instanceof List<?> list) {
                return list.stream().map(SectionNode::plain).toList();
            }
            return value;
        }
    }

    /** A plain map, e.g. one element of a list of maps. */
    final class MapNode implements EditorNode {

        private final Map<String, Object> backing;
        private final Consumer<Map<String, Object>> onChange;

        public MapNode(Map<String, Object> backing, Consumer<Map<String, Object>> onChange) {
            this.backing = backing == null ? new LinkedHashMap<>() : backing;
            this.onChange = onChange;
        }

        public Map<String, Object> backing() {
            return backing;
        }

        @Override
        public Map<String, Object> entries() {
            return new LinkedHashMap<>(backing);
        }

        @Override
        public void put(String key, Object value) {
            if (value == null) {
                remove(key);
                return;
            }
            backing.put(key, value);
            notifyChanged();
        }

        @Override
        public void remove(String key) {
            backing.remove(key);
            notifyChanged();
        }

        @Override
        public EditorNode child(String key) {
            Object value = backing.get(key);
            Map<String, Object> nested;
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                nested = copy;
            } else {
                nested = new LinkedHashMap<>();
            }
            backing.put(key, nested);
            return new MapNode(nested, onChange);
        }

        private void notifyChanged() {
            if (onChange != null) {
                onChange.accept(backing);
            }
        }
    }
}
