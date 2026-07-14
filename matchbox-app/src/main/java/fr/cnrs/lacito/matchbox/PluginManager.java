package fr.cnrs.lacito.matchbox;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class PluginManager {

    private static final Logger logger = Logger.getLogger(
        PluginManager.class.getName()
    );

    private static final String PLUGINS_DIR = "plugins";
    private static final String PLUGINS_DIR_PROPERTY = "matchbox.plugins.dir";

    private static final List<GrammarPlugin> plugins = new ArrayList<>();

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

        Files.list(pluginsDir)
            .filter(path -> path.toString().endsWith(".jar"))
            .forEach(jar -> {
                logger.info("Found jar: " + jar.toAbsolutePath());
                try {
                    URLClassLoader cl = new URLClassLoader(
                        new URL[] { jar.toUri().toURL() },
                        PluginManager.class.getClassLoader()
                    );
                    ServiceLoader<GrammarPlugin> serviceLoader =
                        ServiceLoader.load(GrammarPlugin.class, cl);

                    for (GrammarPlugin plugin : serviceLoader) {
                        plugins.add(plugin);
                        logger.info(
                            "Loaded plugin: " + plugin.getDisplayName()
                        );
                    }

                    // or :
                    //                      GrammarPlugin plugin =
                    //      loader.findFirst()
                    //            .orElseThrow();
                    //             plugins.add(plugin);

                    // the cl should be closed, otherwise classes in the plugin will not be found
                    //cl.close();
                } catch (Exception e) {
                    logger.severe(
                        "Error loading plugin from JAR: " + jar.toAbsolutePath()
                    );
                    e.printStackTrace();
                }
            });

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
