package de.tub.cit.slist.bdos.test;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.tub.cit.slist.bdos.conf.ConfigFactory;
import de.tub.cit.slist.bdos.conf.MemoryLocation;
import de.tub.cit.slist.bdos.conf.OHSConfig;
import de.tub.cit.slist.bdos.conf.SizeType;

public class ConfigTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDefaults() {
		final ConfigFactory factory = new ConfigFactory().withDefaults();
		final OHSConfig conf = factory.build();
		Assert.assertEquals(10, conf.getSize());
		Assert.assertEquals(MemoryLocation.NATIVE_MEMORY, conf.getLocation());
		Assert.assertEquals(SizeType.ELEMENTS, conf.getSizeType());
		Assert.assertEquals(0.3, conf.getDynamicRatio(), 0d);
	}

	@Test
	public void testFallbackDefaults() {
		final ConfigFactory factory = new ConfigFactory();
		final OHSConfig conf = factory.build();
		Assert.assertEquals(100, conf.getSize());
		Assert.assertEquals(MemoryLocation.NATIVE_MEMORY, conf.getLocation());
		Assert.assertEquals(SizeType.ELEMENTS, conf.getSizeType());
		Assert.assertEquals(0.2, conf.getDynamicRatio(), 0d);
	}

	@Test
	public void testSetProperty() {
		final ConfigFactory factory = new ConfigFactory().withDefaults().withLocation(MemoryLocation.BYTE_ARRAY).withSize(Long.MAX_VALUE)
				.withSizeType(SizeType.BYTES).withDynamicRatio(1d);
		final OHSConfig conf = factory.build();
		Assert.assertEquals(Long.MAX_VALUE, conf.getSize());
		Assert.assertEquals(MemoryLocation.BYTE_ARRAY, conf.getLocation());
		Assert.assertEquals(SizeType.BYTES, conf.getSizeType());
		Assert.assertEquals(1d, conf.getDynamicRatio(), 0d);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidEnum() throws FileNotFoundException, IOException {
		new ConfigFactory().withDefaults(this.getClass().getClassLoader().getResourceAsStream("config/invalid.properties"));
	}

	@Test(expected = FileNotFoundException.class)
	public void testInvalidFilename() throws FileNotFoundException, IOException {
		new ConfigFactory().withDefaults("foobar");
	}

}
