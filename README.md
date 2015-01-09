ParseObject AutoParse
============

```java
@AutoParse
public abstract class Profile extends ParseObject implements Parcelable {

  public abstract String name(); // getter
  public String name;

  public abstract List<ParseUser> friends();
  public List<ParseUser> friends;

  public abstract Map<String, ParseUser> friendNameToUserMap();
  public Map<String, ParseUser> friendNameToUserMap;

  public Profile commit() { // dont be abstract method
      return this;
  }

  public static Profile create() {
    return new AutoParse_Profile();
  }
}

...
Profile profile = Profile.create();
profile.name = "Andrew Chen";
profile.friends = getFriends();
profile.friendNameToUserMap = getFriendsMap();

profile.commit().saveInBackground();
...

Profile profile = Profile.create(parseQuery.get("a1b2c3"));
String name = profile.name();
```

Installation
--------

The easy way is to use Gradle.

```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath 'com.android.tools.build:gradle:+'
    classpath 'com.neenbedankt.gradle.plugins:android-apt:+'
  }
}

apply plugin: 'android'
apply plugin: 'android-apt'

dependencies {
  compile 'com.infstory:auto-parse:+'
  apt 'com.infstory:auto-parse-processor:+'
}
```

I recommend using the `android-apt` plugin so that Android Studio picks up the generated files.
Check out the sample project for a working example.

License
-------

    Copyright 2015 8tory, Inc.
    Copyright 2014 Frankie Sardo
    Copyright 2013 Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
