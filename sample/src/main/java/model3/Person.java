package model3;

import auto.parse.AutoParse;
import android.os.Parcelable;
import java.util.List;
import java.util.Map;
import model1.HeightBucket;
import model2.Address;

@AutoParse
public abstract class Person implements Parcelable {
  public static Person create(String name, long id, HeightBucket heightType, Map<String, Address> addresses,
      List<Person> friends) {
    return new AutoParse_Person(name, id, heightType, addresses, friends);
  }

  public abstract String name();
  public abstract long id();
  public abstract HeightBucket heightType();
  public abstract Map<String, Address> addresses();
  public abstract List<Person> friends();
}
