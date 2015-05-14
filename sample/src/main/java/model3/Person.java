package model3;

import auto.parse.AutoParse;
import android.os.Parcelable;
import java.util.List;
import java.util.Map;
import model1.HeightBucket;
import model2.Address;

@AutoParse
public abstract class Person extends com.parse.ParseObject implements Parcelable {
  //public static Person create(String name, long id, HeightBucket heightType, Map<String, Address> addresses,
  //    List<Person> friends) {
  //  return new AutoParse_Person(name, id, heightType, addresses, friends);
  //}

  public static Person create(String name, Long id, Map<String, Address> addresses,
      List<Person> friends) {
    return new AutoParse_Person(name, id, addresses, friends);
  }

  public static Person create() {
    return new AutoParse_Person();
  }

  //public static Person create(String name, Long id) {
    //return new AutoParse_Person(name, id);
  //}

  public abstract String getName();
  public abstract Person setName(String name);
  public abstract Long getId();
  public abstract Person setId(Long id);
  //public abstract HeightBucket heightType();
  public abstract Map<String, Address> getAddresses();
  public abstract Person setAddresses(Map<String, Address> addresses);
  public abstract List<Person> getFriends();
  public abstract Person setFriends(List<Person> friends);

}
