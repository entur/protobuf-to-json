// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.	All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package no.entur.protobuf.json.benchmark.lite;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Timestamp;

/**
 * Writes lite messages as JSON via reflection, looking up the accessors of every message while writing it, like protobuf's
 * {@code MessageLiteToString} does. The baseline for the lite benchmarks.
 */

public class MessageLiteToJson {

	private static final String LIST_SUFFIX = "List";
	private static final String BUILDER_LIST_SUFFIX = "OrBuilderList";
	private static final String MAP_SUFFIX = "Map";
	private static final String BYTES_SUFFIX = "Bytes";
	private static final String VALUE_LIST_SUFFIX = "ValueList";
	private static final String VALUE_SUFFIX = "Value";
	private static final String VALUE_MAP_SUFFIX = "ValueMap";

	// the value of open enums for numbers which are not known
	private static final String UNRECOGNIZED = "UNRECOGNIZED";

	// valid timestamps are from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59.999999999Z, durations up to about 10000 years
	private static final long TIMESTAMP_SECONDS_MIN = -62135596800L;
	private static final long TIMESTAMP_SECONDS_MAX = 253402300799L;
	private static final long DURATION_SECONDS_MAX = 315576000000L;
	private static final int NANOS_MAX = 999999999;

	private static SimpleDateFormat createTimestampFormat() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.setGregorianChange(new Date(Long.MIN_VALUE));
		sdf.setCalendar(calendar);
		return sdf;
	}

	// NOT THREAD SAFE
	protected static final SimpleDateFormat DATE_FORMAT = createTimestampFormat();

	public static void toString(JsonGenerator generator, Object messageLite) throws IOException {
		generator.writeStartObject();
		reflectivePrint(generator, messageLite);
		generator.writeEndObject();
	}

	/**
	 * Reflectively prints the grpc lite object to the buffer at given {@code indent} level.
	 *
	 * @param generator   the output to write to
	 * @param messageLite proto lite message
	 */
	public static void reflectivePrint(JsonGenerator generator, Object messageLite) throws IOException {
		// Build a map of method name to method. We're looking for methods like getFoo(), hasFoo(),
		// getFooList() and getFooMap() which might be useful for building an object's string
		// representation.

		Method[] declaredMethods = messageLite.getClass().getDeclaredMethods();

		Set<String> setters = new HashSet<>(declaredMethods.length * 2);
		Map<String, Method> hazzers = new HashMap<>(declaredMethods.length * 2);
		Map<String, Method> getters = new TreeMap<>();
		for (Method method : declaredMethods) {
			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			if (method.getName().length() < 3) {
				continue;
			}

			if (method.getName().startsWith("set")) {
				setters.add(method.getName());
				continue;
			}

			if (!Modifier.isPublic(method.getModifiers())) {
				continue;
			}

			if (method.getParameterTypes().length != 0) {
				continue;
			}

			if (method.getName().startsWith("has")) {
				hazzers.put(method.getName(), method);
			} else if (method.getName().startsWith("get")) {
				getters.put(method.getName(), method);
			}
		}

		for (Entry<String, Method> getter : getters.entrySet()) {
			String key = getter.getKey();
			if (key.endsWith(LIST_SUFFIX) && !key.endsWith(BUILDER_LIST_SUFFIX)
			// Sometimes people have fields named 'list' that aren't repeated.
					&& !key.equals("get" + LIST_SUFFIX)) {
				// Try to reflectively get the value and toString() the field as if it were repeated. This
				// only works if the method names have not been proguarded out or renamed.

				Method listMethod = getter.getValue();
				if (listMethod != null && listMethod.getReturnType().equals(List.class)) {

					if (key.endsWith(VALUE_LIST_SUFFIX) && isEnums(getters.get(key.substring(0, key.length() - VALUE_LIST_SUFFIX.length()) + LIST_SUFFIX), 0)) {
						// the numbers of an enum list, example: if both getAllowedActionsList and getAllowedActionsValueList, skip
						// getAllowedActionsValueList
						continue;
					}

					List<?> list = (List<?>) invokeOrDie(listMethod, messageLite);
					Method numbers = getters.get(key.substring(0, key.length() - LIST_SUFFIX.length()) + VALUE_LIST_SUFFIX);
					if (numbers != null && containsUnrecognized(list)) {
						list = withNumbers(list, (List<?>) invokeOrDie(numbers, messageLite));
					}
					printField(generator, key.substring(3, key.length() - LIST_SUFFIX.length()), list);
					continue;
				}
			}
			if (key.endsWith(MAP_SUFFIX)
					// Sometimes people have fields named 'map' that aren't maps.
					&& !key.equals("get" + MAP_SUFFIX)) {
				// Try to reflectively get the value and toString() the field as if it were a map. This only
				// works if the method names have not been proguarded out or renamed.
				Method mapMethod = getter.getValue();
				if (mapMethod != null && mapMethod.getReturnType().equals(Map.class)
				// Skip the deprecated getter method with no prefix "Map" when the field name ends with
				// "map", i.e. getStringMap() for a field string_map, which also has getStringMapMap().
				// (Fields marked as deprecated also have deprecated getters, so check the names rather than the annotation.)
						&& !getters.containsKey(key + MAP_SUFFIX)
						// Skip the internal mutable getter method.
						&& Modifier.isPublic(mapMethod.getModifiers())) {

					if (key.endsWith(VALUE_MAP_SUFFIX) && isEnums(getters.get(key.substring(0, key.length() - VALUE_MAP_SUFFIX.length()) + MAP_SUFFIX), 1)) {
						// the numbers of an enum map, example: if both getStatusMap and getStatusValueMap, skip getStatusValueMap
						continue;
					}

					Map<?, ?> map = (Map<?, ?>) invokeOrDie(mapMethod, messageLite);
					Method numbers = getters.get(key.substring(0, key.length() - MAP_SUFFIX.length()) + VALUE_MAP_SUFFIX);
					if (numbers != null && containsUnrecognized(map.values())) {
						map = withNumbers(map, (Map<?, ?>) invokeOrDie(numbers, messageLite));
					}
					printField(generator, key.substring(3, key.length() - MAP_SUFFIX.length()), map);
					continue;
				}
			}

			String suffix = getter.getKey().substring(3);
			if (!setters.contains("set" + suffix)) {
				continue;
			}
			if (key.endsWith(BYTES_SUFFIX)) {
				Method stringMethod = getters.get(key.substring(0, key.length() - BYTES_SUFFIX.length()));
				if (stringMethod != null && stringMethod.getReturnType() == String.class) {
					// Skip bytes based accessors for string fields.
					continue;
				}
			}

			// Try to reflectively get the value and toString() the field as if it were optional. This
			// only works if the method names have not been proguarded out or renamed.
			Method getMethod = getter.getValue();
			Method hasMethod = hazzers.get("has" + suffix);
			// TODO: Fix proto3 semantics.
			if (getMethod != null) {
				Object value = invokeOrDie(getMethod, messageLite);
				final boolean hasValue = hasMethod == null ? !isDefaultValue(value) : (Boolean) invokeOrDie(hasMethod, messageLite);

				if (hasValue) {
					if (getMethod.getReturnType().isPrimitive() && key.endsWith(VALUE_SUFFIX)) {
						Method method = getters.get(key.substring(0, key.length() - VALUE_SUFFIX.length()));
						if (method != null && method.getReturnType().isEnum()) {
							// example: if both getAlgorithm and getAlgorithmValue then skip getAlgorithmValue
							continue;
						}
					}
					if (isUnrecognized(value)) {
						// write the number, example: getAlgorithmValue for getAlgorithm
						Method numberMethod = getters.get(key + VALUE_SUFFIX);
						if (numberMethod != null) {
							value = invokeOrDie(numberMethod, messageLite);
						}
					}
					printField(generator, suffix, value);
				}
			}
		}

		// unknown fields and extensions not included
	}

	/**
	 * Check whether a list or map getter is for an enum field, from the generic return type.
	 *
	 * @param method        list or map getter, or null
	 * @param typeParameter the index of the element type
	 * @return true if the elements are enums, or if this cannot be determined
	 */
	private static boolean isEnums(Method method, int typeParameter) {
		if (method == null) {
			return false;
		}
		Type type = method.getGenericReturnType();
		if (type instanceof ParameterizedType) {
			Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
			return typeParameter < arguments.length && arguments[typeParameter] instanceof Class && ((Class<?>) arguments[typeParameter]).isEnum();
		}
		// the generic signature has been removed (i.e. by shrinking), so assume that it is
		return true;
	}

	private static boolean isUnrecognized(Object value) {
		return value instanceof Enum<?> && ((Enum<?>) value).name().equals(UNRECOGNIZED);
	}

	private static boolean containsUnrecognized(Iterable<?> values) {
		for (Object value : values) {
			if (isUnrecognized(value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Replace unrecognized enum values with their numbers.
	 */
	private static List<Object> withNumbers(List<?> values, List<?> numbers) {
		List<Object> result = new ArrayList<>(values.size());
		for (int i = 0; i < values.size(); i++) {
			Object value = values.get(i);
			result.add(isUnrecognized(value) && i < numbers.size() ? numbers.get(i) : value);
		}
		return result;
	}

	/**
	 * Replace unrecognized enum values with their numbers.
	 */
	private static Map<Object, Object> withNumbers(Map<?, ?> values, Map<?, ?> numbers) {
		Map<Object, Object> result = new LinkedHashMap<>();
		for (Entry<?, ?> entry : values.entrySet()) {
			Object value = entry.getValue();
			result.put(entry.getKey(), isUnrecognized(value) && numbers.containsKey(entry.getKey()) ? numbers.get(entry.getKey()) : value);
		}
		return result;
	}

	private static boolean isDefaultValue(Object o) {
		if (o instanceof Boolean) {
			return !((Boolean) o);
		}
		if (o instanceof Integer) {
			return ((Integer) o) == 0;
		}
		if (o instanceof Long) {
			return ((Long) o) == 0L;
		}
		if (o instanceof Float) {
			return Float.floatToRawIntBits((Float) o) == 0;
		}
		if (o instanceof Double) {
			return Double.doubleToRawLongBits((Double) o) == 0;
		}
		if (o instanceof String) {
			return o.equals("");
		}
		if (o instanceof ByteString) {
			return o.equals(ByteString.EMPTY);
		}
		if (o instanceof MessageLite) { // Can happen in oneofs.
			return o == ((MessageLite) o).getDefaultInstanceForType();
		}
		if (o instanceof Enum<?>) { // Catches oneof enums.
			return ((Enum<?>) o).ordinal() == 0;
		}

		return false;
	}

	/**
	 * Formats a text proto field.
	 *
	 * <p>
	 * For use by generated code only.
	 *
	 * @param buffer the buffer to write to
	 * @param name   the field name (in PascalCase)
	 * @param object the object value of the field
	 */
	static void printField(JsonGenerator buffer, String name, Object object) throws IOException {

		if (name != null) {
			buffer.writeFieldName(toFieldName(name));
		}
		printValue(buffer, object);
	}

	/**
	 * Field name from an accessor name (without the get prefix), i.e. FooBar to fooBar.
	 */
	private static String toFieldName(String name) {
		if (name.isEmpty()) {
			return name;
		}
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	static void printValue(JsonGenerator buffer, Object object) throws IOException {
		if (object instanceof List<?>) {
			List<?> list = (List<?>) object;
			buffer.writeStartArray();
			for (Object entry : list) {
				printValue(buffer, entry);
			}
			buffer.writeEndArray();
			return;
		}
		if (object instanceof Map<?, ?>) {
			buffer.writeStartObject();
			Map<?, ?> map = (Map<?, ?>) object;
			for (Entry<?, ?> entry : map.entrySet()) {
				// map keys are written as is (they are not field names)
				buffer.writeFieldName(entry.getKey().toString());
				printValue(buffer, entry.getValue());
			}
			buffer.writeEndObject();
			return;
		}

		// see special handling:
		// https://github.com/HubSpot/jackson-datatype-protobuf/tree/master/src/main/java/com/hubspot/jackson/datatype/protobuf/builtin/serializers
		// invalid values are written as messages (below)
		if (object instanceof Timestamp && isValid((Timestamp) object)) {
			buffer.writeString(toString((Timestamp) object));
			return;
		} else if (object instanceof Duration && isValid((Duration) object)) {
			buffer.writeString(toString((Duration) object));
			return;
		}

		if (object instanceof GeneratedMessageLite) {
			buffer.writeStartObject();
			reflectivePrint(buffer, object);
			buffer.writeEndObject();
			return;
		}

		if (object instanceof String) {
			buffer.writeString((String) object);
		} else if (object instanceof ByteString) {
			ByteString byteString = (ByteString) object;
			// base64 (java.util.Base64 is not available before Android 8)
			buffer.writeBinary(byteString.toByteArray());
		} else if (object instanceof Boolean) {
			buffer.writeBoolean((Boolean) object);
		} else if (object instanceof Integer) {
			buffer.writeNumber((Integer) object);
		} else if (object instanceof Long) {
			buffer.writeNumber((Long) object);
		} else if (object instanceof Double) {
			buffer.writeNumber((Double) object);
		} else if (object instanceof Float) {
			buffer.writeNumber((Float) object);
		} else if (object instanceof Short) {
			buffer.writeNumber((Short) object);
		} else if (object instanceof Byte) {
			buffer.writeNumber((Byte) object);
		} else {
			buffer.writeString(object.toString());
		}
	}

	/** Calls invoke and throws a RuntimeException if it fails. */
	static Object invokeOrDie(Method method, Object object, Object... params) {
		try {
			return method.invoke(object, params);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Couldn't use Java reflection to implement protocol message reflection.", e);
		} catch (InvocationTargetException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else if (cause instanceof Error) {
				throw (Error) cause;
			} else {
				throw new RuntimeException("Unexpected exception thrown by generated accessor method.", cause);
			}
		}
	}

	public static String toString(Duration duration) {
		long seconds = duration.getSeconds();
		int nanos = duration.getNanos();

		StringBuilder result = new StringBuilder(16);
		if (seconds < 0 || nanos < 0) {
			result.append("-");
			seconds = -seconds;
			nanos = -nanos;
		}
		result.append(seconds);
		if (nanos != 0) {
			result.append(".");
			result.append(formatNanos(nanos));
		}
		result.append("s");
		return result.toString();
	}

	public static String toString(Timestamp timestamp) {
		// assume timestamp is valid, see isValid(..)
		long seconds = timestamp.getSeconds();
		int nanos = timestamp.getNanos();
		StringBuilder result = new StringBuilder(48);
		Date date = new Date(seconds * 1000L);
		String format;
		synchronized (DATE_FORMAT) {
			format = DATE_FORMAT.format(date);
		}
		result.append(format);
		if (nanos != 0) {
			result.append(".");
			result.append(formatNanos(nanos));
		}

		result.append("Z");
		return result.toString();
	}

	static boolean isValid(Timestamp timestamp) {
		long seconds = timestamp.getSeconds();
		int nanos = timestamp.getNanos();
		return seconds >= TIMESTAMP_SECONDS_MIN && seconds <= TIMESTAMP_SECONDS_MAX && nanos >= 0 && nanos <= NANOS_MAX;
	}

	static boolean isValid(Duration duration) {
		long seconds = duration.getSeconds();
		int nanos = duration.getNanos();
		if (seconds < -DURATION_SECONDS_MAX || seconds > DURATION_SECONDS_MAX || nanos < -NANOS_MAX || nanos > NANOS_MAX) {
			return false;
		}
		// seconds and nanos must have the same sign
		return !(seconds < 0 && nanos > 0) && !(seconds > 0 && nanos < 0);
	}

	static String formatNanos(int nanos) {
		if (nanos % 1000000 == 0) {
			return String.format(Locale.ENGLISH, "%1$03d", nanos / 1000000);
		} else {
			return nanos % 1000 == 0 ? String.format(Locale.ENGLISH, "%1$06d", nanos / 1000) : String.format(Locale.ENGLISH, "%1$09d", nanos);
		}
	}

}
