package auto.parse.sample;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import model1.HeightBucket;
import model2.Address;
import model3.Person;

public interface SampleData {

  //static final Person ALICE = Person.create("Alice", 1L, HeightBucket.AVERAGE,
  //    new HashMap<String, Address>() {{
  //      put("home", Address.create(new double[] { 0.3, 0.7 }, "Rome"));
  //    }}, Collections.<Person>emptyList());

  //static final Person ALICE = Person.create("Alice", 1L);

  static final Person ALICE = Person.create("Alice", 1L,
      new HashMap<String, Address>() {{
        put("home", Address.create(new byte[] { 3, 7 }, "Rome"));
      }}, Collections.<Person>emptyList());

  static final Person ANDREW = Person.create();

  //static final Person BOB = Person.create("Bob", 2L, HeightBucket.TALL,
  //    new HashMap<String, Address>() {{
  //  put("home", Address.create(new double[] { 3.2, 143.2 }, "Turin"));
  //  put("work", Address.create(new double[] { 5.9, 156.1 }, "Genoa"));
  //}}, Arrays.asList(ALICE));

  static final Person BOB = Person.create("Bob", 2L,
      new HashMap<String, Address>() {{
    put("home", Address.create(new byte[] { 3, 113 }, "Turin"));
    put("work", Address.create(new byte[] { 5, 126 }, "Genoa"));
  }}, Arrays.asList(ALICE));

  //static final Person BOB = Person.create("Bob", 2L);
}
