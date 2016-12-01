package de.tub.cit.slist.bdos.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class OHSConfig implements java.io.Serializable {

	private static final long	serialVersionUID	= -5759600872480745404L;
	private static final String	RESOURCE_NAME		= "config/serializer.properties";

	private static final String	PROP_SIZE		= "size";
	private static final String	PROP_SIZE_TYPE	= "sizeType";
	private static final String	PROP_LOCATION	= "location";

	private static final Properties defaults;
	static {
		defaults = new Properties();
		defaults.setProperty(PROP_SIZE, "100");
		defaults.setProperty(PROP_SIZE_TYPE, SizeType.ELEMENTS.name());
		defaults.setProperty(PROP_LOCATION, MemoryLocation.NATIVE_MEMORY.name());
	}

	private final Properties properties;

	OHSConfig() {
		properties = new Properties(defaults);
		// loadDefaultsFromFile();
	}

	void loadDefaultsFromFile() {
		try {
			loadDefaultsFromStream(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME));
		} catch (final IOException e) {
			// TODO log exception
			e.printStackTrace();
		}
	}

	void loadDefaultsFromFile(final File f) throws FileNotFoundException, IOException {
		loadDefaultsFromStream(new FileInputStream(f));
	}

	void loadDefaultsFromStream(final InputStream inputStream) throws IOException {
		try (InputStream stream = inputStream) {
			if (stream == null) throw new FileNotFoundException("InputStream is NULL.");
			properties.load(stream);
			validateProperties();
		}
	}

	private void validateProperties() {
		final StringBuilder builder = new StringBuilder();
		try {
			getSizeType();
		} catch (final IllegalArgumentException e) {
			constructEnumExceptionString(builder, PROP_SIZE_TYPE, SizeType.values());
		}
		try {
			getLocation();
		} catch (final IllegalArgumentException e) {
			constructEnumExceptionString(builder, PROP_LOCATION, MemoryLocation.values());
		}
		try {
			getSize();
		} catch (final NumberFormatException e) {
			constructNumberFormatExceptionString(builder, PROP_SIZE, "Long");
		}
		if (builder.length() > 0) // error
			throw new IllegalArgumentException(builder.toString());
	}

	private void constructNumberFormatExceptionString(final StringBuilder builder, final String propName, final String type) {
		builder.append("Illegal value of property '").append(propName).append("': ").append(properties.getProperty(propName))
				.append(". Valid values are of type ").append(type).append(". ");
	}

	@SuppressWarnings("rawtypes")
	private void constructEnumExceptionString(final StringBuilder builder, final String enumName, final Enum[] values) {
		builder.append("Illegal value of property '").append(enumName).append("': ").append(properties.getProperty(enumName)).append(". Valid values are: [");
		int i = 0;
		for (final Enum e : values) {
			if (i++ > 0) {
				builder.append(", ");
			}
			builder.append(e.name());
		}
		builder.append("]. ");
	}

	void setSize(final long size) {
		properties.setProperty(PROP_SIZE, String.valueOf(size));
	}

	public long getSize() {
		return Long.parseLong(properties.getProperty(PROP_SIZE));
	}

	void setSizeType(final SizeType type) {
		properties.setProperty(PROP_SIZE_TYPE, type.name());
	}

	public SizeType getSizeType() {
		return SizeType.valueOf(properties.getProperty(PROP_SIZE_TYPE));
	}

	void setLocation(final MemoryLocation type) {
		properties.setProperty(PROP_LOCATION, type.name());
	}

	public MemoryLocation getLocation() {
		return MemoryLocation.valueOf(properties.getProperty(PROP_LOCATION));
	}

}
