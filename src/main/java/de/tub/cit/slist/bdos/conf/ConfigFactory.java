package de.tub.cit.slist.bdos.conf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Factory class for obtaining instances of {@link OHSConfig}.
 */
public class ConfigFactory {
	private final OHSConfig config;

	public ConfigFactory() {
		config = new OHSConfig();
	}

	/**
	 * Sets the size.
	 *
	 * @param size
	 * @return <code>this</code>
	 */
	public ConfigFactory withSize(final long size) {
		config.setSize(size);
		return this;
	}

	/**
	 * Sets the {@link SizeType}
	 *
	 * @param type
	 * @return <code>this</code>
	 */
	public ConfigFactory withSizeType(final SizeType type) {
		config.setSizeType(type);
		return this;
	}

	/**
	 * Sets the {@link MemoryLocation}
	 *
	 * @param location
	 * @return <code>this</code>
	 */
	public ConfigFactory withLocation(final MemoryLocation location) {
		config.setLocation(location);
		return this;
	}

	/**
	 * Sets the ratio used for dynamic-length elements (Strings, Arrays, Collections)
	 *
	 * @param ratio
	 * @return <code>this</code>
	 */
	public ConfigFactory withDynamicRatio(final double ratio) {
		config.setDynamicRatio(ratio);
		return this;
	}

	/**
	 * Loads defaults from default file.
	 *
	 * @return <code>this</code>
	 *
	 * @see {@link OHSConfig#loadPropertiesFromFile()}
	 */
	public ConfigFactory withDefaults() {
		config.loadPropertiesFromFile();
		return this;
	}

	/**
	 * Loads defaults from specified file.
	 *
	 * @param filename
	 * @return <code>this</code>
	 * @throws FileNotFoundException
	 * @throws IOException
	 *
	 * @see {@link #withDefaults(File)}
	 */
	public ConfigFactory withDefaults(final String filename) throws FileNotFoundException, IOException {
		return withDefaults(new File(filename));
	}

	/**
	 * Loads defaults from given file.
	 *
	 * @param f {@link File}
	 * @return <code>this</code>
	 * @throws FileNotFoundException
	 * @throws IOException
	 *
	 * @see {@link OHSConfig#loadPropertiesFromFile(File)}
	 */
	public ConfigFactory withDefaults(final File f) throws FileNotFoundException, IOException {
		config.loadPropertiesFromFile(f);
		return this;
	}

	/**
	 * Loads defaults from {@link InputStream}.
	 *
	 * @param stream
	 * @return <code>this</code>
	 * @throws FileNotFoundException
	 * @throws IOException
	 *
	 * @see {@link OHSConfig#loadPropertiesFromStream(InputStream)}
	 */
	public ConfigFactory withDefaults(final InputStream stream) throws FileNotFoundException, IOException {
		config.loadPropertiesFromStream(stream);
		return this;
	}

	/**
	 * Constructs the config object.
	 *
	 * @return {@link OHSConfig}
	 */
	public OHSConfig build() {
		config.validateProperties();
		return config;
	}

}
