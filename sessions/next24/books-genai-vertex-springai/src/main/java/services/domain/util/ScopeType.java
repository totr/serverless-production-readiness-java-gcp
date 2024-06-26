/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package services.domain.util;

public enum ScopeType {
    PUBLIC("public"),
    PRIVATE("private");

    private final String value;

    ScopeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScopeType fromValue(String value) {
        for (ScopeType scope : ScopeType.values()) {
            if (scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown enum value : " + value);
    }
}
