/*
 * Copyright (C) 2015 8tory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package auto.parse;

import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import com.parse.ParseFile;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseRelation;
import com.parse.ParseUser;

public class AutoParseHelper {
    public static Object get(ParseObject parseObject, String key, Object defValue) {
        return parseObject.getParseObject(key);
    }

    public static boolean get(ParseObject parseObject, String key, Boolean defValue) {
        return parseObject.getBoolean(key);
    }

    public static byte[] get(ParseObject parseObject, String key, byte[] defValue) {
        return parseObject.getBytes(key);
    }

    public static Date get(ParseObject parseObject, String key, Date defValue) {
        return parseObject.getDate(key);
    }

    public static double get(ParseObject parseObject, String key, Double defValue) {
        return parseObject.getDouble(key);
    }

    public static int get(ParseObject parseObject, String key, Integer defValue) {
        return parseObject.getInt(key);
    }

    public static JSONArray get(ParseObject parseObject, String key, JSONArray defValue) {
        return parseObject.getJSONArray(key);
    }

    public static JSONObject get(ParseObject parseObject, String key, JSONObject defValue) {
        return parseObject.getJSONObject(key);
    }

    public static <T> List<T> get(ParseObject parseObject, String key, List<T> defValue) {
        return (List<T>) parseObject.getList(key);
    }

    public static <V> Map<String, V> get(ParseObject parseObject, String key, Map<String, V> defValue) {
        return (Map<String, V>) parseObject.getMap(key);
    }

    public static <T extends ParseObject> ParseRelation<T> get(ParseObject parseObject, String key, ParseRelation<T> defValue) {
        return (ParseRelation<T>) parseObject.getRelation(key);
    }

    public static long get(ParseObject parseObject, String key, Long defValue) {
        return parseObject.getLong(key);
    }

    public static Number get(ParseObject parseObject, String key, Number defValue) {
        return parseObject.getNumber(key);
    }

    public static ParseFile get(ParseObject parseObject, String key, ParseFile defValue) {
        return parseObject.getParseFile(key);
    }

    public static ParseGeoPoint get(ParseObject parseObject, String key, ParseGeoPoint defValue) {
        return parseObject.getParseGeoPoint(key);
    }

    public static ParseObject get(ParseObject parseObject, String key, ParseObject defValue) {
        return parseObject.getParseObject(key);
    }

    public static ParseUser get(ParseObject parseObject, String key, ParseUser defValue) {
        return parseObject.getParseUser(key);
    }

    public static String get(ParseObject parseObject, String key, String defValue) {
        return parseObject.getString(key);
    }
}
