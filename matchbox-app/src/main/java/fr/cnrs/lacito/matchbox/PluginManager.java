package fr.cnrs.lacito.matchbox;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginManager {

    private static final Logger logger = Logger.getLogger(
        PluginManager.class.getName()
    );

    private static final String PLUGINS_DIR = "plugins";
    private static final String PLUGINS_DIR_PROPERTY = "matchbox.plugins.dir";

    private static final List<GrammarPlugin> plugins = new ArrayList<>();
    private static final List<ClassLoader> pluginClassLoaders = new ArrayList<>();

    public static final GrammarPlugin getPluginById(String id) {
        return plugins
            .stream()
            .filter(plugin -> plugin.getId().equals(id))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("No plugin found with ID: " + id)
            );
    }

    public static List<GrammarPlugin> getAvailableGrammars() {
        return plugins;
    }

    public static List<GrammarPlugin> loadPlugins() throws IOException {
        logger.info("Loading plugins...");
        Path pluginsDir = findPluginsDirectory();
        if (pluginsDir == null) {
            logger.warning(
                "No plugins directory found. No plugins will be loaded."
            );
            return Collections.emptyList();
        }

        logger.info(
            "Found " +
                Files.list(pluginsDir).count() +
                " files in plugins directory."
        );
        Files.list(pluginsDir).forEach(x ->
            logger.info("Found file: " + x.getFileName())
        );

        try (var jars = Files.list(pluginsDir)) {
            jars
                .filter(path -> path.toString().endsWith(".jar"))
                .sorted()
                .forEach(PluginManager::loadJar);
        }

        logger.info(
            "Found plugins: " +
                String.join(
                    ", ",
                    plugins
                        .stream()
                        .map(GrammarPlugin::getDisplayName)
                        .toArray(String[]::new)
                )
        );

        return plugins;
    }

    private static void loadJar(Path jar) {
        logger.info("Found jar: " + jar.toAbsolutePath());
        try {
            // Not closed: the plugin classes are loaded lazily for the whole session.
            URLClassLoader cl = new URLClassLoader(
                new URL[] { jar.toUri().toURL() },
                PluginManager.class.getClassLoader()
            );
            boolean found = false;
            for (GrammarPlugin plugin : ServiceLoader.load(GrammarPlugin.class, cl)) {
                if (plugin.getClass().getClassLoader() != cl) {
                    continue; // provided by the application or another jar
                }
                found = true;
                int version = plugin.getApiVersion();
                if (version != GrammarPlugin.API_VERSION) {
                    logger.severe(
                        "Plugin " + plugin.getId() + " in " + jar.getFileName() +
                            " uses grammar API version " + version + ", expected " +
                            GrammarPlugin.API_VERSION + "; rebuild it. Skipped."
                    );
                    continue;
                }
                plugins.add(plugin);
                logger.info("Loaded plugin: " + plugin.getDisplayName());
            }
            if (found) {
                pluginClassLoaders.add(cl);
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            // e.g. a plugin compiled against an older API (NoClassDefFoundError, AbstractMethodError)
            logger.log(Level.SEVERE,
                "Incompatible plugin " + jar.getFileName() + ": rebuild it against grammar API version " +
                    GrammarPlugin.API_VERSION + ". Skipped.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading plugin from JAR: " + jar.toAbsolutePath(), e);
        }
    }

    /**
     * Loads the providers of a service declared by the application and by the plugin jars
     * ({@code META-INF/services}), application providers first. Each provider class appears once:
     * {@code ServiceLoader} on a plugin class loader also returns the parent's providers.
     * A provider that fails to load is logged and skipped.
     */
    public static <S> List<S> loadServices(Class<S> service) {
        Map<String, S> providers = new LinkedHashMap<>();
        List<ClassLoader> loaders = new ArrayList<>();
        loaders.add(PluginManager.class.getClassLoader());
        loaders.addAll(pluginClassLoaders);
        for (ClassLoader loader : loaders) {
            Iterator<S> it = ServiceLoader.load(service, loader).iterator();
            while (true) {
                try {
                    if (!it.hasNext()) break;
                    S provider = it.next();
                    providers.putIfAbsent(provider.getClass().getName(), provider);
                } catch (ServiceConfigurationError | LinkageError e) {
                    logger.log(Level.SEVERE, "Cannot load a provider of " + service.getName(), e);
                }
            }
        }
        return List.copyOf(providers.values());
    }

    /** True if the class was loaded from a plugin jar rather than from the application. */
    public static boolean isFromPlugin(Class<?> type) {
        return pluginClassLoaders.contains(type.getClassLoader());
    }

    private static Path findPluginsDirectory() {
        String pluginsPropertyPath = System.getProperty(
            PLUGINS_DIR_PROPERTY,
            ""
        );
        if (!pluginsPropertyPath.isEmpty()) {
            Path path = Paths.get(pluginsPropertyPath);
            if (Files.isDirectory(path)) {
                logger.info(
                    "Found plugins directory in system property: " +
                        pluginsPropertyPath
                );
                return path;
            } else {
                logger.warning(
                    "Specified plugins directory does not exist: " +
                        pluginsPropertyPath +
                        "; absolute path: " +
                        path.toAbsolutePath()
                );
            }
        } else {
            logger.info(
                "No plugins directory system properties: " +
                    PLUGINS_DIR_PROPERTY
            );
        }

        Path devPath = Paths.get(PLUGINS_DIR);
        if (Files.isDirectory(devPath)) {
            logger.info(
                "Looking for plugins in current directory: " +
                    devPath.toAbsolutePath()
            );
            return devPath;
        } else {
            logger.info(
                "No plugins directory found in current directory (" +
                    devPath.toAbsolutePath() +
                    ")."
            );
        }

        try {
            Path appDir = Paths.get(
                PluginManager.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
            )
                .getParent()
                .getParent();
            Path runtimePath = appDir.resolve(PLUGINS_DIR);

            if (Files.isDirectory(runtimePath)) {
                logger.info(
                    "Found plugins in runtime directory: " +
                        runtimePath.toAbsolutePath()
                );
                return runtimePath;
            } else {
                logger.info(
                    "No plugins directory found in runtime directory (" +
                        runtimePath.toAbsolutePath() +
                        ")."
                );
            }
        } catch (Exception e) {
            logger.severe("Unable to find a plugins directory.");
            e.printStackTrace();
        }

        return null;
    }
}
