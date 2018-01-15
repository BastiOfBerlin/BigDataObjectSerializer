package de.tub.cit.slist.bdos.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for the Off-Heap Serializer. Instances can be obtained by using the {@link ConfigFactory}.
 */
public class OHSConfig implements java.io.Serializable {
	final transient Logger logger = LoggerFactory.getLogger(OHSConfig.class);

	private static final long	serialVersionUID	= 6112706157710557887L;
	private static final String	RESOURCE_NAME		= "config/serializer.properties";

	private static final String	PROP_SIZE		= "size";
	private static final String	PROP_SIZE_TYPE	= "sizeType";
	private static final String	PROP_LOCATION	= "location";
	private static final String	PROP_RATIO		= "dynamicRatio";

	private static final Properties defaults;
	static {
		defaults = new Properties();
		defaults.setProperty(PROP_SIZE, "100");
		defaults.setProperty(PROP_SIZE_TYPE, SizeType.ELEMENTS.name());
		defaults.setProperty(PROP_LOCATION, MemoryLocation.NATIVE_MEMORY.name());
		defaults.setProperty(PROP_RATIO, "0.2");
	}

	private final Properties properties;

	/**
	 * Constructor loading the coded {@link #defaults} as last fallback.
	 */
	OHSConfig() {
		properties = new Properties(defaults);
	}

	/**
	 * loads properties from file using default resource name {@value #RESOURCE_NAME}
	 */
	void loadPropertiesFromFile() {
		try {
			loadPropertiesFromStream(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME));
		} catch (final IOException e) {
			logger.warn("Could not read from file: {}", e.getMessage());
		}
	}

	/**
	 * loads properties from a file f.
	 *
	 * @param f {@link File}
	 * @throws IOException
	 */
	void loadPropertiesFromFile(final File f) throws IOException {
		loadPropertiesFromStream(new FileInputStream(f));
	}

	/**
	 * loads properties from an InputStream.
	 *
	 * @param inputStream {@link InputStream}
	 * @throws IOException
	 */
	void loadPropertiesFromStream(final InputStream inputStream) throws IOException {
		try (InputStream stream = inputStream) {
			if (stream == null) throw new FileNotFoundException("InputStream is NULL.");
			properties.load(stream);
			validateProperties();
		}
	}

	/**
	 * Validates the properties by checking for valid enum constants and number formats. Throws an {@link IllegalArgumentException} if any property is found
	 * invalid.
	 */
	void validateProperties() {
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
			final long size = getSize();
			if (size < 1) {
				constructInvalidValueRangeString(builder, PROP_SIZE, String.valueOf(size), "1", "maxInt");
			}
		} catch (final NumberFormatException e) {
			constructNumberFormatExceptionString(builder, PROP_SIZE, "Long");
		}
		try {
			final double ratio = getDynamicRatio();
			if (ratio < 0 || ratio > 1) {
				constructInvalidValueRangeString(builder, PROP_SIZE, String.valueOf(ratio), "0.0", "1.0");
			}
		} catch (final NumberFormatException e) {
			constructNumberFormatExceptionString(builder, PROP_RATIO, "Double");
		}
		if (builder.length() > 0) // error
			throw new IllegalArgumentException(builder.toString());
	}

	private void constructNumberFormatExceptionString(final StringBuilder builder, final String propName, final String type) {
		builder.append("Illegal value of property '").append(propName).append("': ").append(properties.getProperty(propName))
				.append(". Valid values are of type ").append(type).append(".\n");
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
		builder.append("].\n");
	}

	private void constructInvalidValueRangeString(final StringBuilder builder, final String propName, final String value, final String minValue,
			final String maxValue) {
		builder.append("Illegal value range for property '").append(propName).append("': ").append(value).append(". [").append(minValue).append("-")
				.append(maxValue).append("].\n");
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

	void setDynamicRatio(final double ratio) {
		properties.setProperty(PROP_RATIO, String.valueOf(ratio));
	}

	public double getDynamicRatio() {
		return Double.parseDouble(properties.getProperty(PROP_RATIO));
	}

	@Override
	public String toString() {
		return "OHSConfig [size=" + getSize() + ", sizeType=" + getSizeType() + ", location=" + getLocation() + ", dynamicRatio=" + getDynamicRatio() + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((properties == null) ? 0 : properties.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final OHSConfig other = (OHSConfig) obj;
		if (properties == null) {
			if (other.properties != null) return false;
		} else if (!properties.equals(other.properties)) return false;
		return true;
	}

}
