/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
 *
 * This file is part of Flupke, a HTTP3 client Java library
 *
 * Flupke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Flupke is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package tech.kwik.flupke.test;

import java.lang.reflect.Field;

public class FieldReader {

    private final Object target;
    private final Field field;

    public FieldReader(Object target, Class type, String fieldName) throws Exception {
        this.target = target;
        this.field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
    }

    public FieldReader(Object target, Field field) {
        this.target = target;
        this.field = field;
        field.setAccessible(true);
    }

    public Object read() {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read " + field.getName() + " of object", e);
        }
    }
}
