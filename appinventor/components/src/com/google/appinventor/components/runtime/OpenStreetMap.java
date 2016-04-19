// -*- mode: java; c-basic-offset: 2; -*- // Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

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
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import org.osmdroid.bonuspack.overlays.FolderOverlay;
import org.osmdroid.bonuspack.overlays.InfoWindow;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.bonuspack.overlays.MapEventsReceiver;
import org.osmdroid.bonuspack.overlays.MapEventsOverlay;
import org.osmdroid.api.IMapController;
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

  private final int DEFAULT_ZOOM_LEVEL = 16;

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
    map.setBuiltInZoomControls(true);
    map.setMultiTouchControls(true);

    // Set starting point for map
    GeoPoint startPoint = new GeoPoint(42.3601, -71.0589);
    mapController = map.getController();
    mapController.setZoom(DEFAULT_ZOOM_LEVEL);
    mapController.setCenter(startPoint);

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

  @DesignerProperty 
  @SimpleFunction (description = "Sets the zoom level of the map. Valid zoom levels span from 0 to 18 where 18 is most zoomed in.")
  public void ZoomLevel(int zoom) {
    mapController.setZoom(zoom);
  }

  @SimpleProperty (description = "Gets the current zoom level of the map")
  public int ZoomLevel() {
    return map.getZoomLevel();
  }
  
  @SimpleFunction (description = "Sets tile source")
  public void EnableBingSatelliteImagery(String bing_key) {
    bing_key.toString();
  }
  
  @SimpleFunction (description = "Enables Markers")
  public void EnableMarkers(boolean enable) {
  }

  @SimpleFunction (description = "Enables FreeDrawnMarkers")
  public void EnableFreeDrawnMarkers(boolean enable) {
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
      return new BitmapDrawable(getBitmap(pResId));
    }
  }
}
