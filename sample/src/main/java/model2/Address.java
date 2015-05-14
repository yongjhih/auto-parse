package model2;

import auto.parse.AutoParse;
import android.os.Parcelable;

@AutoParse
public abstract class Address extends com.parse.ParseObject implements Parcelable {
  public abstract byte[] getCoordinates();
  public abstract Address setCoordinates(byte[] coordinates);
  public abstract String getCityName();
  public abstract Address setCityName(String name);

  public static Address create() {
    return new AutoParse_Address();
  }

  public static Address create(byte[] coordinates, String cityName) {
    return new AutoParse_Address(coordinates, cityName);
  }
}
