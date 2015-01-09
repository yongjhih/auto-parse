package model2;

import auto.parse.AutoParse;
import android.os.Parcelable;

@AutoParse
public abstract class Address implements Parcelable {
  public abstract double[] coordinates();
  public abstract String cityName();

  public static Address create(double[] coordinates, String cityName) {
    return new AutoParse_Address(coordinates, cityName);
  }
}
