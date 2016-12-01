package de.tub.cit.slist.bdos.conf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ConfigFactory {
	private final OHSConfig config;

	public ConfigFactory() {
		config = new OHSConfig();
	}

	public ConfigFactory withSize(final long size) {
		config.setSize(size);
		return this;
	}

	public ConfigFactory withSizeType(final SizeType type) {
		config.setSizeType(type);
		return this;
	}

	public ConfigFactory withLocation(final MemoryLocation location) {
		config.setLocation(location);
		return this;
	}

	public ConfigFactory withDefaults() {
		config.loadDefaultsFromFile();
		return this;
	}

	public ConfigFactory withDefaults(final String filename) throws FileNotFoundException, IOException {
		return withDefaults(new File(filename));
	}

	public ConfigFactory withDefaults(final File f) throws FileNotFoundException, IOException {
		config.loadDefaultsFromFile(f);
		return this;
	}

	public ConfigFactory withDefaults(final InputStream stream) throws FileNotFoundException, IOException {
		config.loadDefaultsFromStream(stream);
		return this;
	}

	public OHSConfig build() {
		return config;
	}

}
