/*
 * Copyright (c) 2025 Auxio Project
 * JStringRef.cpp is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "JStringRef.h"
#include "util.h"

#include <memory>
#include <stdexcept>

JStringRef::JStringRef(JNIEnv *env, jstring jString) : env(env), string(jString) {
}

JStringRef::JStringRef(JNIEnv *env, const TagLib::String string) {
    this->env = env;
    this->string = env->NewStringUTF(string.toCString(true));
}

JStringRef::~JStringRef() {
    env->DeleteLocalRef(string);
}

TagLib::String JStringRef::copy() {
    if (string == nullptr) {
        throw std::runtime_error("Cannot copy a null Java string");
    }
    const auto release = [this](const char *chars) {
        env->ReleaseStringUTFChars(string, chars);
    };
    // Release the JNI buffer before its local reference, including when the
    // TagLib string allocation throws. TagLib owns its copied string data.
    std::unique_ptr<const char, decltype(release)> chars(
            env->GetStringUTFChars(string, nullptr), release);
    if (!chars) {
        throw std::runtime_error("Unable to acquire Java string UTF chars");
    }
    return TagLib::String(chars.get());
}

jstring& JStringRef::operator*() {
    return string;
}
