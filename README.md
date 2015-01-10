AutoParse for ParseObject 
============

before:

```java
ParseObject gameScore = new ParseObject("GameScore");
gameScore.put("score", 1337);
gameScore.put("playerName", "Sean Plott");
gameScore.put("cheatMode", false);
gameScore.saveInBackground();
```

after:

```java
ParseGameScore gameScore = ParseGameScore.create();
gameScore.score = 1337;
gameScore.playerName = "Sean Plott";
gameScore.cheatMode = false;
ParseGameScore.commit().saveInBackground();
```

Usage
-----

```java
@ParseClassName("GameScore")
@AutoParse
public abstract class ParseGameScore extends ParseObject implements Parcelable {

  public abstract String playerName(); // getString("playerName");
  public String playerName;            // put("playerName", name);

  public abstract Integer score();
  public Integer score;

  public abstract Boolean cheatMode();
  public Boolean cheatMode; 

  public Profile commit() { // dont be abstract method
      return this;
  }

  public static Profile create() {
    return new AutoParse_Profile();
  }
}
```

Installation
--------

```gradle
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

See Also
-----

auto-parse uses compile-time annotation that's faster than runtime annotation reflection.

* https://github.com/8tory/simple-parse via runtime annotation reflection, slower.

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
