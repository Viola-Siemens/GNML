package com.hexagram2021.gnml.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GnmlAccountPolicyTest {
	@Test
	void oldAccountMetadataIsTaggedHmclAndHidden() {
		TestJsonObject metadata = new TestJsonObject();
		Object account = new Object();

		GnmlAccountPolicy.markLoaded(account, metadata);

		assertEquals(GnmlAccountPolicy.TAG_HMCL, metadata.getString(GnmlAccountPolicy.TAG_PROPERTY));
		assertFalse(GnmlAccountPolicy.isVisible(account));
		assertEquals(null, GnmlAccountPolicy.visibleOrNull(account));
	}

	@Test
	void newlySerializedAccountIsTaggedGnmlAndVisible() {
		TestJsonObject metadata = new TestJsonObject();
		Object account = new Object();

		GnmlAccountPolicy.tagSerialized(new TestSerializedAccount(metadata), account);

		assertEquals(GnmlAccountPolicy.TAG_GNML, metadata.getString(GnmlAccountPolicy.TAG_PROPERTY));
		assertTrue(GnmlAccountPolicy.isVisible(account));
	}

	@Test
	void onlyGnmlMicrosoftAccountUnlocksAdditionalLoginTypes() {
		Object account = new org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount();

		GnmlAccountPolicy.markAdded(account);

		assertTrue(GnmlAccountPolicy.allowsAdditionalAccounts());
	}

	public static final class TestJsonObject {
		private final java.util.Map<String, String> properties = new java.util.HashMap<>();

		public TestJsonElement get(String key) {
			String value = properties.get(key);
			return value == null ? null : new TestJsonElement(value);
		}

		public void addProperty(String key, String value) {
			properties.put(key, value);
		}

		public String getString(String key) {
			return properties.get(key);
		}
	}

	public static final class TestJsonElement {
		private final String value;

		TestJsonElement(String value) {
			this.value = value;
		}

		public String getAsString() {
			return value;
		}
	}

	public static final class TestSerializedAccount {
		private final TestJsonObject metadata;

		TestSerializedAccount(TestJsonObject metadata) {
			this.metadata = metadata;
		}

		public TestJsonObject metadata() {
			return metadata;
		}
	}
}