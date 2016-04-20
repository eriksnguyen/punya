// -*- mode: java; c-basic-offset: 2; -*- // Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.overlays.FolderOverlay;
import org.osmdroid.bonuspack.overlays.InfoWindow;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.bonuspack.overlays.MapEventsReceiver;
import org.osmdroid.bonuspack.overlays.MapEventsOverlay;
import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@DesignerComponent(version = YaVersion.OPEN_STREET_MAP_COMPONENT_VERSION,
    description = "Visible component that show information on OpenStreetMap map.",
    category = ComponentCategory.MAPVIZ)
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.ACCESS_COARSE_LOCATION, "
    + "android.permission.ACCESS_FINE_LOCATION, "
    + "android.permission.ACCESS_WIFI_STATE, "
    + "android.permission.ACCESS_NETWORK_STATE, "
    + "android.permission.INTERNET, "
    + "android.permission.WRITE_EXTERNAL_STORAGE")
@UsesLibraries(libraries = "slf4j-android.jar,osmdroid-android.jar,osmbonuspack.jar,commons-lang.jar,gson-2.1.jar") // SLF4J not req for osmdroid v 5.0+
public class OpenStreetMap extends AndroidViewComponent{

  // Class defaults
  private static final int     DEFAULT_ZOOM_LEVEL = 16;
  private static final boolean DEFAULT_MARKERS_ENABLED = true;
  private static final boolean DEFAULT_HAND_DRAWN_REGIONS_ENABLED = false;
  private static final boolean DEFAULT_ZOOM_CONTROLS = true;
  private static final boolean DEFAULT_MULTI_TOUCH_CONTROLS = true;
  private static final double  DEFAULT_MAP_CENTER_LATITUDE = 42.3601; // Downtown Boston
  private static final double  DEFAULT_MAP_CENTER_LONGITUDE = -71.0589; // Downtown Boston
  private static final GeoPoint DEFAULT_MAP_CENTER = new GeoPoint(DEFAULT_MAP_CENTER_LATITUDE,
                                                                  DEFAULT_MAP_CENTER_LONGITUDE);

  private boolean markersEnabled = DEFAULT_MARKERS_ENABLED;
  private boolean handDrawnRegionsEnabled = DEFAULT_HAND_DRAWN_REGIONS_ENABLED;
  private boolean zoomControls = DEFAULT_ZOOM_CONTROLS;
  private boolean multiTouchControls = DEFAULT_MULTI_TOUCH_CONTROLS;

  private final Activity context;
  private final Form form;
  private static final String TAG = "OpenStreetMap";
  // Layout
  // We create thie LinerLayout and add our mapFragment in it.
  // private final com.google.appinventor.components.runtime.LinearLayout viewLayout;
  // private final FrameLayout viewLayout;
  // private final android.widget.LinearLayout viewLayout;
  // private LinearLayout viewLayout;
  private android.widget.LinearLayout viewLayout;

  private MapView map;
  private IMapController mapController;
  private ResourceProxy customResourceProxy;
  private FolderOverlay markerOverlay;
  private org.osmdroid.views.MapView.LayoutParams mapParams;

  private MapEventsReceiver mapEventReceiver;
  private MapEventsOverlay  mapEventOverlay;

  // translates App Inventor alignment codes to Android gravity
  // private final AlignmentUtil alignmentSetter;
  private Bundle savedInstanceState;

  private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    /**
   * Creates a OpenStreetMap component.
   *
   * @param container container, component will be placed in
   */
  public OpenStreetMap(ComponentContainer container) {
    super(container);
    Log.i(TAG, "Beginning OpenStreetMap Constructor");
    context = container.$context();
    form = container.$form();
    savedInstanceState = form.getOnCreateBundle();
    Log.i(TAG, "SavedInstanceState of OSM: " + savedInstanceState);

    // The layout that the map v
    viewLayout = new android.widget.LinearLayout(context);
    viewLayout.setId(generateViewId());

    // initialize the map
    initializeMap();
    initializeMapParams();

    // Add the map to the view layout
    viewLayout.addView(map, mapParams);

    container.$add(this);
    Width(LENGTH_FILL_PARENT);
    Height(LENGTH_FILL_PARENT);
  }

  /*
  * Initializes the map with default values
  */
  private void initializeMap() {
    customResourceProxy = new CustomResourceProxy(context);
    map = new MapView(context, 256, customResourceProxy);  // TODO: osmdroid 4.3 has bad resolution bug
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setBuiltInZoomControls(zoomControls);
    map.setMultiTouchControls(multiTouchControls);

    // Set starting point for map
    mapController = map.getController();
    mapController.setZoom(DEFAULT_ZOOM_LEVEL);
    mapController.setCenter(DEFAULT_MAP_CENTER);

    // Set up the overlay so that the map can receive touch events
    initializeEventReceiver();

    // Set up any other overlays
    initializeOverlays();

    // Redraw the map
    map.invalidate();
  }

  // Sets up the parameters of the map for display
  private void initializeMapParams() {
    mapParams = new org.osmdroid.views.MapView.LayoutParams(
        org.osmdroid.views.MapView.LayoutParams.FILL_PARENT,  // Width
        org.osmdroid.views.MapView.LayoutParams.FILL_PARENT,  // Height
        null, 0, 0, 0);                                       // geopoint, alignment, offsetX, offsetY
  }

  private void initializeEventReceiver() {
    mapEventReceiver = new MapEventsReceiver() {

      @Override
      public boolean singleTapConfirmedHelper(GeoPoint p) {
        Toast.makeText(context, "Tap on (" + p.getLatitude() + "," + p.getLongitude() + ")", Toast.LENGTH_SHORT).show();
        InfoWindow.closeAllInfoWindowsOn(map);
        return true;
      }

      @Override
      public boolean longPressHelper(GeoPoint p) {
        //if(!drawable) {
          drawMarker(p);
        //}

        return true;
      }
    };

    mapEventOverlay = new MapEventsOverlay(context, mapEventReceiver);
    map.getOverlays().add(0, mapEventOverlay); // Ensure that it is the lowest overlay level
  }

  private void initializeOverlays() {
    markerOverlay = new FolderOverlay(context);
    map.getOverlays().add(markerOverlay);
  }

  private void drawMarker(GeoPoint p) {
    //Toast.makeText(context, "Attempting to make a marker", Toast.LENGTH_SHORT).show();
    Marker startMarker = new Marker(map, customResourceProxy);
    startMarker.setPosition(p);
    startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
    //startMarker.setIcon(Drawable.createFromPath("./osm_images/marker_default.png"));
    //startMarker.setInfoWindow(new AnnotationInfoWindow(R.layout.bonuspack_bubble, map));
    startMarker.setDraggable(true);
    markerOverlay.add(startMarker);
    map.invalidate();
  }

  /**
   * Generate a value suitable for use in .
   * This value will not collide with ID values generated at build time by aapt for R.id.
   *
   * @return a generated ID value
   */
  private static int generateViewId() {
    for (;;) {
      final int result = sNextGeneratedId.get();
      // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
      int newValue = result + 1;
      if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
      if (sNextGeneratedId.compareAndSet(result, newValue)) {
        return result;
      }
    }
  }

  //  Currently this doesn't work
  @Override
  @SimpleProperty()
  public void Width(int width) {
    if (width == LENGTH_PREFERRED) {
      width = LENGTH_FILL_PARENT;
    }
    super.Width(width);
  }

  @Override
  @SimpleProperty()
  public void Height(int height) {
    if (height == LENGTH_PREFERRED) {
      height = LENGTH_FILL_PARENT;
    }
    super.Height(height);
  }

  @Override
  public View getView() {
    return viewLayout;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
    defaultValue = "16")
  @SimpleProperty (description = "Sets the zoom level of the map. Valid zoom levels span from 0 to 18 where 18 is most zoomed in.")
  public void ZoomLevel(int zoom) {
    mapController.setZoom(zoom);
    map.invalidate();
  }

  @SimpleProperty (description = "Gets the current zoom level of the map")
  public int ZoomLevel() {
    return map.getZoomLevel();
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
  @SimpleProperty (description = "Sets zoom controls")
  public void ZoomControls(Boolean enable) {
    zoomControls = enable;
    map.setBuiltInZoomControls(zoomControls);
    map.invalidate();
  }

  @SimpleProperty (description = "Gets zoom control settings")
  public boolean ZoomControls() {
    return zoomControls;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
  @SimpleProperty (description = "Sets multi-touch controls")
  public void MultiTouchControls(Boolean enable) {
    multiTouchControls = enable;
    map.setMultiTouchControls(multiTouchControls);
    map.invalidate();
  }

  @SimpleProperty (description = "Gets multi-touch control settings")
  public boolean MultiTouchControls() {
    return multiTouchControls;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
  @SimpleProperty (description = "Enables Markers")
  public void MarkersEnabled(Boolean enable) {
    markersEnabled = enable;
    //TODO: do something
  }

  @SimpleProperty (description = "Returns whether markers are enabled")
  public boolean MarkersEnabled() {
    return markersEnabled;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "False")
  @SimpleProperty (description = "Enables HandDrawnRegions")
  public void HandDrawnRegionsEnabled(Boolean enable) {
    handDrawnRegionsEnabled = enable;
    //TODO: do something
  }

  @SimpleProperty (description = "Returns whether HandDrawnRegions are enabled")
  public boolean HandDrawnRegionsEnabled() {
    return handDrawnRegionsEnabled;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
    defaultValue = "-71.0589")
  @SimpleProperty (description = "Sets the latitude of the center of the map")
  public void MapCenterLongitude(double longitude) {
    GeoPoint center = (GeoPoint) map.getMapCenter();
    int currLongitude = center.getLongitudeE6();
    int lon = (int) (longitude * 1E6);  // Convert from decimal to integer based coordinates
    if (currLongitude != lon) {
        center.setLongitudeE6(lon);
        mapController.setCenter(center);
        map.invalidate();
    }
  }

  @SimpleProperty (description = "Gets the latitude of the center of the map")
  public double MapCenterLongitude() {
    return map.getMapCenter().getLongitude();
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_FLOAT,
    defaultValue = "42.3601")
  @SimpleProperty (description = "Sets the longitude of the center of the map")
  public void MapCenterLatitude(double latitude) {
    GeoPoint center = (GeoPoint) map.getMapCenter();
    int currLatitude = center.getLatitudeE6();
    int lat = (int) (latitude * 1E6);  // Convert from decimal to integer based coordinates
    if (currLatitude != lat) {
        center.setLatitudeE6(lat);
        mapController.setCenter(center);
        map.invalidate();
    }
  }

  @SimpleProperty (description = "Gets the longitude of the center of the map")
  public double MapCenterLatitude() {
    return map.getMapCenter().getLatitude();
  }

  @SimpleFunction (description = "Sets the map center based on the given coordinates")
  public void SetMapCenter(double latitude, double longitude) {
    GeoPoint center = (GeoPoint) map.getMapCenter();
    int currLatitude = center.getLatitudeE6();
    int currLongitude = center.getLongitudeE6();
    int lat = (int) (latitude * 1E6);
    int lon = (int) (longitude * 1E6);
    if ( currLatitude != lat || currLongitude != lon) {
        center.setLatitudeE6(lat);
        center.setLongitudeE6(lon);
        mapController.setCenter(center);
        map.invalidate();
    }
  }

  @SimpleFunction (description = "Sets tile source")
  public void EnableBingSatelliteImagery(String bing_key) {
    bing_key.toString();
  }

  private class CustomResourceProxy extends org.osmdroid.DefaultResourceProxyImpl {

    private final Context mContext;
    public CustomResourceProxy(Context pContext) {
      super(pContext);
      mContext = pContext;
    }

    @Override
    public Bitmap getBitmap(final bitmap pResId) {
      try {
          int id = form.getResources().getIdentifier(pResId.name(), "drawable", form.getPackageName());
          switch (pResId) {
            case center:
            case direction_arrow:
            case marker_default:
            case marker_default_focused_base:
            case navto_small:
            case next:
            case previous:
            case person:
            case ic_menu_offline:
            case ic_menu_mylocation:
            case ic_menu_compass:
            case ic_menu_mapmode: return BitmapFactory.decodeResource(form.getResources(), id);
          }
      } catch (final OutOfMemoryError ignore) {
          Toast.makeText(context, "Exception: " + ignore.getMessage(), Toast.LENGTH_SHORT).show();
      }
      //return super.getBitmap(pResId);
      return null;
    }

    @Override
    public Drawable getDrawable(final bitmap pResId) {
      Drawable d;

      try {
        d = MediaUtil.getBitmapDrawable(form, "res/drawable/" + pResId.name());
      } catch (IOException e) {
        d = new BitmapDrawable(getBitmap(pResId));
      }

      return d;
    }
  }
}
