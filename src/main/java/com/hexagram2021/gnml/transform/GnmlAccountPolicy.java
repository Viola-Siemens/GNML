package com.hexagram2021.gnml.transform;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * HMCL 账户标签与可用性策略。通过反射避免 GNML 编译期依赖 HMCL 或 JavaFX。
 */
public final class GnmlAccountPolicy {
	public static final String TAG_PROPERTY = "gnmlTag";
	public static final String TAG_HMCL = "HMCL";
	public static final String TAG_GNML = "GNML";

	private static final Map<Object, String> ACCOUNT_TAGS = Collections.synchronizedMap(new IdentityHashMap<>());
	private static final String ACCOUNT_LIST_PAGE = "org.jackhuang.hmcl.ui.account.AccountListPage";

	public static void markLoaded(Object account, Object metadata) {
		if (account == null) {
			return;
		}
		String tag = readTag(metadata);
		if (!TAG_GNML.equals(tag)) {
			tag = TAG_HMCL;
			writeTag(metadata, tag);
		}
		ACCOUNT_TAGS.put(account, tag);
	}

	public static void markAdded(Object account) {
		if (account != null) {
			ACCOUNT_TAGS.put(account, TAG_GNML);
			updateRestriction();
		}
	}

	public static void tagSerialized(Object serializedAccount, Object account) {
		if (serializedAccount == null || account == null) {
			return;
		}
		String tag = ACCOUNT_TAGS.get(account);
		if (tag == null) {
			tag = TAG_GNML;
			ACCOUNT_TAGS.put(account, tag);
		}
		try {
			Object metadata = readSerializedMetadata(serializedAccount);
			writeTag(metadata, tag);
			if (TAG_GNML.equals(tag)) {
				updateRestriction();
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法写入 GNML 账户标签", e);
		}
	}

	public static boolean isVisible(Object account) {
		return account != null && TAG_GNML.equals(ACCOUNT_TAGS.get(account));
	}

	public static boolean isMicrosoftAccount(Object account) {
		for (Class<?> type = account == null ? null : account.getClass(); type != null; type = type.getSuperclass()) {
			if ("org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount".equals(type.getName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean allowsAdditionalAccounts() {
		synchronized (ACCOUNT_TAGS) {
			for (Map.Entry<Object, String> entry : ACCOUNT_TAGS.entrySet()) {
				if (TAG_GNML.equals(entry.getValue()) && isMicrosoftAccount(entry.getKey())) {
					return true;
				}
			}
		}
		return false;
	}

	public static Object visibleAccounts(Object accounts) {
		if (accounts == null) {
			return null;
		}
		try {
			ClassLoader loader = accounts.getClass().getClassLoader();
			Class<?> observableList = Class.forName("javafx.collections.ObservableList", false, loader);
			Class<?> filteredList = Class.forName("javafx.collections.transformation.FilteredList", true, loader);
			Constructor<?> constructor = filteredList.getConstructor(observableList, Predicate.class);
			return constructor.newInstance(accounts, (Predicate<Object>) GnmlAccountPolicy::isVisible);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法创建 HMCL 账户过滤列表", e);
		}
	}

	public static Object visibleMappedAccounts(Object accounts, Object mapper) {
		try {
			ClassLoader loader = mapper.getClass().getClassLoader();
			Class<?> mappedList = Class.forName("org.jackhuang.hmcl.util.javafx.MappedObservableList", true, loader);
			Class<?> observableList = Class.forName("javafx.collections.ObservableList", false, loader);
			Class<?> function = Class.forName("java.util.function.Function", false, loader);
			return mappedList.getMethod("create", observableList, function)
					.invoke(null, visibleAccounts(accounts), mapper);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法创建 GNML 账户展示列表", e);
		}
	}

	public static Object visibleOrNull(Object account) {
		return isVisible(account) ? account : null;
	}

	public static Object restrictFactory(Object factory) {
		if (factory == null || allowsAdditionalAccounts()) {
			return factory;
		}
		try {
			ClassLoader loader = factory.getClass().getClassLoader();
			Class<?> accountsClass = Class.forName("org.jackhuang.hmcl.setting.Accounts", true, loader);
			return accountsClass.getField("FACTORY_MICROSOFT").get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法限制 HMCL 账户登录方式", e);
		}
	}

	public static void setSelectedAccountProperty(Object property, Object account) {
		try {
			property.getClass().getMethod("set", Object.class).invoke(property, visibleOrNull(account));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法清理 HMCL 账户选择", e);
		}
	}

	public static Object loginIfVisible(Object account) throws Exception {
		if (!isVisible(account)) {
			return null;
		}
		try {
			return account.getClass().getMethod("logIn").invoke(account);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
	}

	public static void persistLoadedTags() {
		try {
			ClassLoader loader = GnmlAccountPolicy.class.getClassLoader();
			Class<?> accountsClass = Class.forName("org.jackhuang.hmcl.setting.Accounts", false, loader);
			Method update = accountsClass.getDeclaredMethod("updateAccountMetadataRecords");
			update.setAccessible(true);
			update.invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法保存 HMCL 账户标签", e);
		}
	}

	public static void updateRestriction() {
		try {
			ClassLoader loader = GnmlAccountPolicy.class.getClassLoader();
			Class<?> pageClass = Class.forName(ACCOUNT_LIST_PAGE, false, loader);
			Field restricted = pageClass.getDeclaredField("RESTRICTED");
			restricted.setAccessible(true);
			Object property = restricted.get(null);
			property.getClass().getMethod("set", boolean.class).invoke(property, !allowsAdditionalAccounts());
		} catch (ClassNotFoundException ignored) {
			// The account page has not been loaded yet.
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法更新 HMCL 账户添加限制", e);
		}
	}

	private static String readTag(Object metadata) {
		if (metadata == null) {
			return null;
		}
		try {
			Object value = metadata.getClass().getMethod("get", String.class).invoke(metadata, TAG_PROPERTY);
			return value == null ? null : String.valueOf(value.getClass().getMethod("getAsString").invoke(value));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法读取 HMCL 账户标签", e);
		}
	}

	private static void writeTag(Object metadata, String tag) {
		if (metadata == null) {
			return;
		}
		try {
			metadata.getClass().getMethod("addProperty", String.class, String.class)
					.invoke(metadata, TAG_PROPERTY, tag);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("无法写入 HMCL 账户标签", e);
		}
	}

	private static Object readSerializedMetadata(Object serializedAccount) throws ReflectiveOperationException {
		try {
			Method accessor = serializedAccount.getClass().getDeclaredMethod("metadata");
			accessor.setAccessible(true);
			return accessor.invoke(serializedAccount);
		} catch (NoSuchMethodException ignored) {
			Field field = serializedAccount.getClass().getDeclaredField("metadata");
			field.setAccessible(true);
			return field.get(serializedAccount);
		}
	}

	private GnmlAccountPolicy() {
	}
}