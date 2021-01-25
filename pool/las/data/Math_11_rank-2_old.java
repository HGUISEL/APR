/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.parameter;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.lang.reflect.Array;
import java.io.IOException;
import java.text.Format;
import java.text.FieldPosition;
import javax.measure.unit.Unit;
import org.opengis.util.GenericName;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.util.InternationalString;
import org.opengis.util.NameSpace;
import org.apache.sis.measure.Range;
import org.apache.sis.measure.RangeFormat;
import org.apache.sis.internal.referencing.NameToIdentifier;
import org.apache.sis.internal.util.X364;

import static org.apache.sis.internal.util.X364.*;
import static org.apache.sis.util.CharSequences.spaces;


/**
 * A row in the table to be formatted by {@link ParameterFormat}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.4 (derived from geotk-3.00)
 * @version 0.4
 * @module
 */
final class ParameterTableRow {
    /**
     * The (<var>codespace(s)</var>, <var>name(s)</var>) entries for the identifier and all aliases
     * declared in the constructor. The codespace key may be null, but the name values shall never be null.
     *
     * <p>Values can be of two kinds:</p>
     * <ul>
     *   <li>{@link String} for names or aliases.</li>
     *   <li>{@link ReferenceIdentifier} for identifiers.</li>
     * </ul>
     *
     * @see #addIdentifier(String, Object)
     */
    private final Map<String,Set<Object>> identifiers;

    /**
     * The largest codespace width, in number of Unicode code points.
     *
     * @see #addIdentifier(String, Object)
     */
    int codespaceWidth;

    /**
     * The string representation of the domain of values, or {@code null} if none.
     *
     * @see #setValueDomain(Range, Format, StringBuffer)
     */
    String valueDomain;

    /**
     * The position to use for alignment of {@link #valueDomain}.
     * This is usually after the '…' separator.
     *
     * @see #setValueDomain(Range, Format, StringBuffer)
     */
    int valueDomainAlignment;

    /**
     * The values. Some elements in this list may be null.
     *
     * @see #addValue(Object, Unit)
     */
    final List<Object> values;

    /**
     * The units of measurement. The size of this list shall be the same than {@link #values}.
     * The list may contain null elements.
     *
     * <p>This list is initially filled with {@link Unit} instance. Later in the formatting process,
     * {@code Unit} instances will be replaced by their symbol.</p>
     *
     * @see #addValue(Object, Unit)
     */
    final List<Object> units;

    /**
     * Creates a new row in a table to be formatted by {@link ParameterFormat}.
     *
     * @param object The object for which to get the (<var>codespace(s)</var>, <var>name(s)</var>).
     * @param locale The locale for formatting the names.
     */
    ParameterTableRow(final IdentifiedObject object, final Locale locale, final boolean brief) {
        values = new ArrayList<>(2); // In the vast majority of cases, we will have only one value.
        units  = new ArrayList<>(2);
        /*
         * Creates a collection which will contain the identifier and all aliases
         * found for the given IdentifiedObject. We begin with the primary name.
         */
        identifiers = new LinkedHashMap<>();
        final ReferenceIdentifier identifier = object.getName();
        addIdentifier(identifier.getCodeSpace(), identifier.getCode()); // Value needs to be a String here.
        if (!brief) {
            final Collection<GenericName> aliases = object.getAlias();
            if (aliases != null) { // Paranoiac check.
                for (GenericName alias : aliases) {
                    String codespace = NameToIdentifier.getCodeSpace(alias);
                    if (codespace != null) {
                        alias = alias.tip();
                    } else {
                        final NameSpace scope = alias.scope();
                        if (scope != null && !scope.isGlobal()) {
                            codespace = toString(scope.name().tip(), locale);
                        }
                    }
                    addIdentifier(codespace, toString(alias, locale));
                }
            }
            final Collection<? extends ReferenceIdentifier> ids = object.getIdentifiers();
            if (ids != null) { // Paranoiac check.
                for (final ReferenceIdentifier id : ids) {
                    addIdentifier(id.getCodeSpace(), id); // No .getCode() here.
                }
            }
        }
    }

    /**
     * Helper method for the constructor only, adding an identifier for the given code space.
     * As a side effect, this method remembers the length of the widest code space.
     */
    private void addIdentifier(final String codespace, final Object identifier) {
        if (codespace != null) {
            final int width = codespace.codePointCount(0, codespace.length());
            if (width > codespaceWidth) {
                codespaceWidth = width;
            }
        }
        Set<Object> ids = identifiers.get(codespace);
        if (ids == null) {
            ids = new LinkedHashSet<>(8);
            identifiers.put(codespace, ids);
        }
        ids.add(identifier);
    }

    /**
     * Sets the value domain to the string representation of the given range.
     *
     * @param  range  The range to format.
     * @param  format The format to use for formatting the {@code range}.
     * @param  buffer A temporary buffer to use for formatting the range.
     * @return The position of a character on which to align the text in the cell.
     */
    final int setValueDomain(final Range<?> range, final Format format, final StringBuffer buffer) {
        final FieldPosition fieldPosition = new FieldPosition(RangeFormat.Field.MAX_VALUE);
        valueDomain = format.format(range, buffer, fieldPosition).toString();
        buffer.setLength(0);
        return valueDomainAlignment = fieldPosition.getBeginIndex();
    }

    /**
     * Adds a value and its unit of measurement.
     *
     * @param value The value, or {@code null}.
     * @param unit  The unit of measurement, or {@code null}.
     */
    final void addValue(final Object value, final Unit<?> unit) {
        values.add(value);
        units .add(unit);
    }

    /**
     * If the list has only one element and this element is an array or a collection, expands it.
     * This method shall be invoked only after the caller finished to add all elements in the
     * {@link #values} and {@link #units} lists.
     */
    final void expandSingleton() {
        assert values.size() == units.size();
        if (values.size() == 1) {
            Object value = values.get(0);
            if (value != null) {
                if (value instanceof Collection<?>) {
                    value = ((Collection<?>) value).toArray();
                }
                if (value.getClass().isArray()) {
                    final int length = Array.getLength(value);
                    final Object unit = units.get(0);
                    values.clear();
                    units.clear();
                    for (int i=0; i<length; i++) {
                        values.add(Array.get(value, i));
                        units.add(unit);
                    }
                }
            }
        }
    }

    /**
     * Writes the given color if {@code colorEnabled} is {@code true}.
     */
    private static void writeColor(final Appendable out, final X364 color, final boolean colorEnabled)
            throws IOException
    {
        if (colorEnabled) {
            out.append(color.sequence());
        }
    }

    /**
     * Writes the identifiers. At most one of {@code colorsForTitle} and {@code colorsForRows}
     * can be set to {@code true}.
     *
     * <p><b>This method can be invoked only once per {@code ParameterTableRow} instance</b>,
     * at its implementation destroys the internal list of identifiers.</p>
     *
     * @param  out             Where to write.
     * @param  colorsForTitle  {@code true} if syntax coloring should be applied for table title.
     * @param  colorsForRows   {@code true} if syntax coloring should be applied for table rows.
     * @param  lineSeparator   The system-dependent line separator.
     * @throws IOException     If an exception occurred while writing.
     */
    final void writeIdentifiers(final Appendable out, final boolean colorsForTitle,
            final boolean colorsForRows, final String lineSeparator) throws IOException
    {
        boolean isNewLine = false;
        for (final Map.Entry<String,Set<Object>> entry : identifiers.entrySet()) {
            final String codespace = entry.getKey();
            final Set<Object> identifiers = entry.getValue();
            Iterator<Object> it = identifiers.iterator();
            while (it.hasNext()) {
                if (isNewLine) {
                    out.append(lineSeparator);
                }
                isNewLine = true;
                /*
                 * Write the codespace. More than one name may exist for the same codespace,
                 * in which case the code space will be repeated on a new line each time.
                 */
                writeColor(out, FOREGROUND_GREEN, colorsForTitle);
                int pad = codespaceWidth + 1;
                if (codespace != null) {
                    writeColor(out, FAINT, colorsForRows);
                    out.append(codespace).append(':');
                    writeColor(out, NORMAL, colorsForRows);
                    pad -= codespace.length();
                }
                /*
                 * Write the name or alias after the codespace. We remove what we wrote,
                 * because we may iterate over the 'identifiers' set more than once.
                 */
                out.append(spaces(pad));
                writeColor(out, BOLD, colorsForTitle);
                out.append(toString(it.next()));
                writeColor(out, RESET, colorsForTitle);
                it.remove();
                /*
                 * Write all identifiers between parenthesis after the firt name only.
                 * Aliases (to be written in a new iteration) will not have identifier.
                 */
                boolean hasAliases     = false;
                boolean hasIdentifiers = false;
                while (it.hasNext()) {
                    final Object id = it.next();
                    if (id instanceof ReferenceIdentifier) {
                        out.append(hasIdentifiers ? ", " : " (");
                        writeColor(out, FOREGROUND_YELLOW, colorsForTitle);
                        out.append(toString(id));
                        writeColor(out, FOREGROUND_DEFAULT, colorsForTitle);
                        hasIdentifiers = true;
                        it.remove();
                    } else {
                        hasAliases = true;
                    }
                }
                if (hasIdentifiers) {
                    out.append(')');
                }
                if (hasAliases) {
                    it = identifiers.iterator();
                }
            }
        }
    }

    /**
     * Returns a string representation of the given name in the given locale, with paranoiac checks against null value.
     * Such null values should never happen since the properties used here are mandatory, but we try to make this class
     * robust to broken implementations.
     */
    private static String toString(final GenericName name, final Locale locale) {
        if (name != null) {
            final InternationalString i18n = name.toInternationalString();
            return (i18n != null) ? i18n.toString(locale) : name.toString();
        }
        return null;
    }

    /**
     * Returns the string representation of the given parameter name.
     */
    private static String toString(Object parameter) {
        if (parameter instanceof ReferenceIdentifier) {
            parameter = ((ReferenceIdentifier) parameter).getCode();
        }
        return parameter.toString();
    }
}
