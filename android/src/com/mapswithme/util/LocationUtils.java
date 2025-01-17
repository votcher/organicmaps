package com.mapswithme.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.mapswithme.maps.location.LocationHelper;
import com.mapswithme.util.log.Logger;
import com.mapswithme.util.log.LoggerFactory;

public class LocationUtils
{
  private LocationUtils() {}

  private static final Logger LOGGER = LoggerFactory.INSTANCE.getLogger(LoggerFactory.Type.LOCATION);
  private static final String TAG = LocationUtils.class.getSimpleName();
  private static final double DEFAULT_SPEED_MPS = 5;

  /**
   * Correct compass angles due to display orientation.
   */
  public static double correctCompassAngle(int displayOrientation, double angle)
  {
    double correction = 0;
    switch (displayOrientation)
    {
    case Surface.ROTATION_0:
      return angle;
    case Surface.ROTATION_90:
      correction = Math.PI / 2.0;
      break;
    case Surface.ROTATION_180:
      correction = Math.PI;
      break;
    case Surface.ROTATION_270:
      correction = (3.0 * Math.PI / 2.0);
      break;
    }

    return correctAngle(angle, correction);
  }

  public static double correctAngle(double angle, double correction)
  {
    double res = angle + correction;

    final double twoPI = 2.0 * Math.PI;
    res %= twoPI;

    // normalize angle into [0, 2PI]
    if (res < 0.0)
      res += twoPI;

    return res;
  }

  public static boolean isExpired(Location l, long millis, long expirationMillis)
  {
    long timeDiff;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
      timeDiff = (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1000000;
    else
      timeDiff = System.currentTimeMillis() - millis;
    return (timeDiff > expirationMillis);
  }

  public static double getTimeDiff(Location lastLocation, Location newLocation)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
      return (newLocation.getElapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos()) * 1.0E-9;
    else
    {
      long time = newLocation.getTime();
      long lastTime = lastLocation.getTime();
      if (!isSameLocationProvider(newLocation.getProvider(), lastLocation.getProvider()))
      {
        // Do compare current and previous system times in case when
        // we have incorrect time settings on a device.
        time = System.currentTimeMillis();
        lastTime = LocationHelper.INSTANCE.getSavedLocationTime();
      }

      return (time - lastTime) * 1.0E-3;
    }
  }

  private static boolean isSameLocationProvider(String p1, String p2)
  {
    return (p1 != null && p1.equals(p2));
  }

  public static boolean isFromGpsProvider(@NonNull Location location)
  {
    return LocationManager.GPS_PROVIDER.equals(location.getProvider());
  }

  public static boolean isFromFusedProvider(@NonNull Location location)
  {
    return LocationManager.FUSED_PROVIDER.equals(location.getProvider());
  }

  public static boolean isAccuracySatisfied(@NonNull Location location)
  {
    // If it's a gps location then we completely ignore an accuracy checking,
    // because there are cases on some devices (https://jira.mail.ru/browse/MAPSME-3789)
    // when location is good, but it doesn't contain an accuracy for some reasons.
    if (isFromGpsProvider(location))
      return true;

    // Completely ignore locations without lat and lon.
    return location.getAccuracy() > 0.0f;
  }

  public static boolean isLocationBetterThanLast(@NonNull Location newLocation, @NonNull Location lastLocation)
  {
    if (isFromFusedProvider(newLocation))
      return true;

    if (isFromGpsProvider(lastLocation) && lastLocation.getAccuracy() == 0.0f)
      return true;

    double speed = Math.max(DEFAULT_SPEED_MPS, (newLocation.getSpeed() + lastLocation.getSpeed()) / 2.0);
    double lastAccuracy = lastLocation.getAccuracy() + speed * LocationUtils.getTimeDiff(lastLocation, newLocation);
    return newLocation.getAccuracy() < lastAccuracy;
  }


  @SuppressLint("InlinedApi")
  @SuppressWarnings("deprecation")
  public static boolean areLocationServicesTurnedOn(@NonNull Context context)
  {
    final ContentResolver resolver = context.getContentResolver();
    try
    {
      return Settings.Secure.getInt(resolver, Settings.Secure.LOCATION_MODE)
             != Settings.Secure.LOCATION_MODE_OFF;
    } catch (Settings.SettingNotFoundException e)
    {
      e.printStackTrace();
      return false;
    }
  }
}
