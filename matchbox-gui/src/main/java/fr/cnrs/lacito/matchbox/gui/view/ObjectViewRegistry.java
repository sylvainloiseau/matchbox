package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;

/**
 * Chooses the {@link ObjectViewFactory} of a corpus object: the factory registered for the
 * closest supertype of the object's class (the class itself first), then the highest priority,
 * then plugin factories before built-in ones. Falls back to {@link GenericObjectViewFactory}.
 *
 * <p>Calls to factories are guarded: an exception in a plugin's view or column falls back to the
 * generic view or an empty cell, and is logged once per factory.
 */
public class ObjectViewRegistry {

    private static final Logger logger = Logger.getLogger(ObjectViewRegistry.class.getName());

    private final List<ObjectViewFactory<?>> factories;
    private final Predicate<Class<?>> isFromPlugin;
    private final ObjectViewFactory<CorpusObject> fallback = new GenericObjectViewFactory();
    private final Map<Class<?>, ObjectViewFactory<?>> cache = new HashMap<>();
    private final Set<Object> reported = Collections.newSetFromMap(new IdentityHashMap<>());

    public ObjectViewRegistry(List<? extends ObjectViewFactory<?>> factories, Predicate<Class<?>> isFromPlugin) {
        this.factories = List.copyOf(factories);
        this.isFromPlugin = isFromPlugin;
    }

    /** Registry with the built-in factories and those of the plugin jars. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ObjectViewRegistry load() {
        List<ObjectViewFactory<?>> factories = (List) PluginManager.loadServices(ObjectViewFactory.class);
        logger.info("Object view factories: " + factories.stream().map(f -> f.getClass().getName()).toList());
        return new ObjectViewRegistry(factories, PluginManager::isFromPlugin);
    }

    /** The factory used for objects of the given class. */
    public ObjectViewFactory<?> factoryFor(Class<?> type) {
        return cache.computeIfAbsent(type, this::resolve);
    }

    private ObjectViewFactory<?> resolve(Class<?> type) {
        Map<Class<?>, Integer> distances = supertypeDistances(type);
        return factories.stream()
            .filter(f -> f.getType() != null && distances.containsKey(f.getType()))
            .min(Comparator
                .<ObjectViewFactory<?>>comparingInt(f -> distances.get(f.getType()))
                .thenComparing(Comparator.<ObjectViewFactory<?>>comparingInt(ObjectViewFactory::getPriority).reversed())
                .thenComparing(f -> isFromPlugin.test(f.getClass()) ? 0 : 1))
            .<ObjectViewFactory<?>>map(f -> f)
            .orElse(fallback);
    }

    /** Breadth-first distance from {@code type} to each of its supertypes (itself = 0). */
    static Map<Class<?>, Integer> supertypeDistances(Class<?> type) {
        Map<Class<?>, Integer> distances = new HashMap<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        distances.put(type, 0);
        queue.add(type);
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            int d = distances.get(c);
            List<Class<?>> parents = new ArrayList<>(List.of(c.getInterfaces()));
            if (c.getSuperclass() != null) {
                parents.add(0, c.getSuperclass());
            }
            for (Class<?> p : parents) {
                if (!distances.containsKey(p)) {
                    distances.put(p, d + 1);
                    queue.add(p);
                }
            }
        }
        return distances;
    }

    /** Detailed view of an object; falls back to the generic view if the factory fails. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Node createView(CorpusObject object, ViewContext context) {
        ObjectViewFactory factory = factoryFor(object.getClass());
        if (factory != fallback) {
            try {
                Node node = factory.createView(object, context);
                if (node != null) {
                    return node;
                }
            } catch (RuntimeException | LinkageError e) {
                report(factory, "createView", e);
            }
        }
        return fallback.createView(object, context);
    }

    /**
     * Shows a re-parsed version of {@code object} in {@code view}, if the factory supports it.
     *
     * @return false if the view must be rebuilt
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean updateView(Node view, CorpusObject previous, CorpusObject object, ViewContext context) {
        ObjectViewFactory factory = factoryFor(object.getClass());
        if (view == null || previous == null || factory == fallback || factory != factoryFor(previous.getClass())
            || !previous.getKind().equals(object.getKind())) {
            return false;
        }
        try {
            return factory.updateView(view, object, context);
        } catch (RuntimeException | LinkageError e) {
            report(factory, "updateView", e);
            return false;
        }
    }

    /** Read-only table columns for objects of the given class. */
    public List<TableColumn<CorpusObject, String>> columnsFor(Class<?> type) {
        return columnsFor(type, null, message -> {});
    }

    /**
     * Table columns for objects of the given class: a line column, then the factory's columns,
     * or a label column if the factory has none. Columns showing a field are editable when a
     * context is given.
     *
     * @param context used to write edited cells; {@code null}: read-only columns
     * @param status  receives the message of a refused edit (empty when an edit is applied)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<TableColumn<CorpusObject, String>> columnsFor(Class<?> type, ViewContext context, Consumer<String> status) {
        ObjectViewFactory factory = factoryFor(type);
        List<ObjectColumn<CorpusObject>> specs;
        try {
            specs = context == null ? factory.getColumns() : factory.getColumns(context);
        } catch (RuntimeException | LinkageError e) {
            report(factory, "getColumns", e);
            specs = List.of();
        }
        List<TableColumn<CorpusObject, String>> columns = new ArrayList<>();
        TableColumn<CorpusObject, String> line = column(
            factory,
            new ObjectColumn<>("Line", o -> String.valueOf(o.getTextCoordinate().startLine()))
        );
        line.setPrefWidth(50);
        line.setEditable(false);
        columns.add(line);
        if (specs == null || specs.isEmpty()) {
            TableColumn<CorpusObject, String> label = column(factory, new ObjectColumn<>("Label", CorpusObject::getLabel));
            label.setEditable(false);
            columns.add(label);
        } else {
            for (ObjectColumn<CorpusObject> spec : specs) {
                TableColumn<CorpusObject, String> column = column(factory, spec);
                if (context != null && spec.field().isPresent()) {
                    String field = spec.field().get();
                    column.setEditable(true);
                    column.setCellFactory(c -> new FieldCell(field, context, status));
                } else {
                    column.setEditable(false);
                }
                columns.add(column);
            }
        }
        return columns;
    }

    private TableColumn<CorpusObject, String> column(Object factory, ObjectColumn<CorpusObject> spec) {
        TableColumn<CorpusObject, String> column = new TableColumn<>(spec.title());
        column.setPrefWidth(200);
        column.setCellValueFactory(cell -> {
            String value;
            try {
                value = spec.value().apply(cell.getValue());
            } catch (RuntimeException | LinkageError e) {
                report(factory, "column " + spec.title(), e);
                value = "";
            }
            return new ReadOnlyStringWrapper(value).getReadOnlyProperty();
        });
        return column;
    }

    private void report(Object factory, String what, Throwable e) {
        if (reported.add(factory)) {
            logger.log(Level.WARNING, "View factory " + factory.getClass().getName() + " failed in " + what, e);
        }
    }
}
