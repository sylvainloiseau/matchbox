package fr.cnrs.lacito.matchbox.gui.assist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API keys of the providers. They are never stored in {@code settings.json} (which users may copy
 * or share): an environment variable is used if set, otherwise {@code api-keys.json} in the
 * settings directory, readable by the user only.
 */
public final class ApiKeyStore {

    private static final Logger logger = Logger.getLogger(ApiKeyStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String FILE = "api-keys.json";

    private final Path file;
    private final Function<String, String> environment;

    public ApiKeyStore(Path configDir) {
        this(configDir, System::getenv);
    }

    ApiKeyStore(Path configDir, Function<String, String> environment) {
        this.file = configDir.resolve(FILE);
        this.environment = environment;
    }

    public Path file() {
        return file;
    }

    /** True if the key of this provider comes from its environment variable. */
    public boolean isFromEnvironment(ProviderKind kind) {
        return kind.needsApiKey() && present(environment.apply(kind.apiKeyVariable()));
    }

    /** The key to use: environment variable first, then the file. */
    public Optional<String> get(ProviderKind kind) {
        if (!kind.needsApiKey()) {
            return Optional.empty();
        }
        String env = environment.apply(kind.apiKeyVariable());
        if (present(env)) {
            return Optional.of(env.strip());
        }
        return Optional.ofNullable(read().get(kind.id())).filter(ApiKeyStore::present);
    }

    /** The key stored in the file (not the environment), for editing. */
    public String stored(ProviderKind kind) {
        return read().getOrDefault(kind.id(), "");
    }

    /** Stores (or removes, if blank) the key of a provider. */
    public void set(ProviderKind kind, String key) throws IOException {
        Map<String, String> keys = read();
        if (key == null || key.isBlank()) {
            if (keys.remove(kind.id()) == null) return;
        } else {
            keys.put(kind.id(), key.strip());
        }
        write(keys);
    }

    private Map<String, String> read() {
        if (!Files.exists(file)) {
            return new TreeMap<>();
        }
        try {
            return new TreeMap<>(MAPPER.readValue(file.toFile(), new TypeReference<Map<String, String>>() {}));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot read " + file + ": " + e.getMessage());
            return new TreeMap<>();
        }
    }

    private void write(Map<String, String> keys) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE + ".tmp");
        Files.deleteIfExists(tmp);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), keys);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
