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
import org.osmdroid.bonuspack.kml.KmlDocument;
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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
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
  private static final boolean DEFAULT_SAVE_ANNOTATIONS_IN_KML = false;
  private static final double  DEFAULT_MAP_CENTER_LATITUDE = 42.3601; // Downtown Boston
  private static final double  DEFAULT_MAP_CENTER_LONGITUDE = -71.0589; // Downtown Boston
  private static final GeoPoint DEFAULT_MAP_CENTER = new GeoPoint(DEFAULT_MAP_CENTER_LATITUDE,
                                                                  DEFAULT_MAP_CENTER_LONGITUDE);

  // Instance variables that aren't taken care of by the wrapped map component
  private boolean markersEnabled = DEFAULT_MARKERS_ENABLED;
  private boolean handDrawnRegionsEnabled = DEFAULT_HAND_DRAWN_REGIONS_ENABLED;
  private boolean zoomControlsEnabled = DEFAULT_ZOOM_CONTROLS;
  private boolean multiTouchControlsEnabled = DEFAULT_MULTI_TOUCH_CONTROLS;
  private boolean saveAnnotationsInKMLEnabled = DEFAULT_SAVE_ANNOTATIONS_IN_KML;

  // Instance variables for logical operations
  private boolean penEnabled = false;

  // Android classes
  private final Activity context;
  private final Form form;
  private static final String TAG = "OpenStreetMap";

  // Layout
  // We create thie LinerLayout and add our mapFragment in it.
  // private final com.google.appinventor.components.runtime.LinearLayout viewLayout;
  // private final FrameLayout viewLayout;
  // private final android.widget.LinearLayout viewLayout;
  // private LinearLayout viewLayout;
  private FrameLayout viewLayout;

  // The Map alongside various necessary tools to control the map
  private MapView map;
  private IMapController mapController;                       // Controls map zoom
  private ResourceProxy customResourceProxy;                  // Overrides the location of resource dependencies for osmdroid
  private org.osmdroid.views.MapView.LayoutParams mapParams;  // Parameters for putting the map into the view layout
  private MapEventsReceiver mapEventsReceiver;                // Listener for map events (primarily presses) in the background

  // Overlays that can be added to the map
  private FolderOverlay markersOverlay;           // Overlay that holds the markers for the map
  private FolderOverlay handDrawnRegionsOverlay;  // Overlay that holds the regions drawn
  private MapEventsOverlay mapEventsOverlay;      // Overlay that handles sensing map events (presses) in the background

  // KML files used for saves
  private String KMLFilePath = "";
  private KmlDocument saveDocument = null;

  // translates App Inventor alignment codes to Android gravity
  // private final AlignmentUtil alignmentSetter;
  private Bundle savedInstanceState;

  private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

  // hash map to fake r.id for layout views
  private final HashMap<String, Integer> viewIds = new HashMap<String, Integer>();

  // Display density
  private float DISPLAY_DENSITY;

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

    DISPLAY_DENSITY = form.getResources().getDisplayMetrics().density;

    // The layout that the map v
    viewLayout = new FrameLayout(context);
    setId(viewLayout, "viewLayout", generateViewId());

    // initialize the map
    initializeMap();

    // Programmatically set up the layout necessary for the component
    initializeView();

    container.$add(this);
    Width(LENGTH_FILL_PARENT);
    Height(LENGTH_FILL_PARENT);
  }

  /*
  * Initializes the map with default values
  */
  private void initializeMap() {
    // Set up the map parameters for display
    mapParams = new org.osmdroid.views.MapView.LayoutParams(
        org.osmdroid.views.MapView.LayoutParams.FILL_PARENT,  // Width
        org.osmdroid.views.MapView.LayoutParams.FILL_PARENT,  // Height
        null, 0, 0, 0);                                       // geopoint, alignment, offsetX, offsetY

    // Initialize the resource proxy for finding drawable items under res/
    customResourceProxy = new CustomResourceProxy(context);

    // Create the map
    map = new MapView(context, 256, customResourceProxy);  // TODO: osmdroid 4.3 has bad resolution bug
    setId(map, "map", generateViewId());

    // Set starting point for map alongside other defaults
    mapController = map.getController();
    mapController.setCenter(DEFAULT_MAP_CENTER);
    mapController.setZoom(DEFAULT_ZOOM_LEVEL);
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setBuiltInZoomControls(zoomControlsEnabled);
    map.setMultiTouchControls(multiTouchControlsEnabled);

    // Set up the overlay so that the map can receive touch events
    mapEventsReceiver = new MapEventsReceiver() {
      @Override
      public boolean singleTapConfirmedHelper(GeoPoint p) {
        Toast.makeText(context, "Tap on (" + p.getLatitude() + "," + p.getLongitude() + ")", Toast.LENGTH_SHORT).show();
        InfoWindow.closeAllInfoWindowsOn(map);
        return true;
      }

      @Override
      public boolean longPressHelper(GeoPoint p) {
        if(markersEnabled) {
          drawMarker(p);
        }

        return true;
      }
    };

    // Set up any other overlays
    initializeOverlays();

    // Redraw the map
    map.invalidate();
  }

  private void initializeOverlays() {
    markersOverlay = new FolderOverlay(context);
    map.getOverlays().add(markersOverlay);

    handDrawnRegionsOverlay = new FolderOverlay(context);
    map.getOverlays().add(handDrawnRegionsOverlay);

    // Ensure that it is the lowest overlay level because this is the fallback
    // that is used to capture events that aren't taken by other overlay items
    // like markers or free drawn regions.
    mapEventsOverlay = new MapEventsOverlay(context, mapEventsReceiver);
    map.getOverlays().add(0, mapEventsOverlay);
  }

  /*
   * Initialize the viewLayout according to the following xml doc:
    <?xml version="1.0" encoding="utf-8"?>
    <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:orientation="vertical"
        android:layout_width="fill_parent"
        android:layout_height="fill_parent">
        <org.osmdroid.views.MapView android:id="@+id/map"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent" />

        <FrameLayout
            android:id="@+id/drawFrame"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <Button
                android:id="@+id/penButton"
                android:layout_height="42dp"
                android:layout_width="42dp"
                android:layout_gravity="bottom|right"
                android:layout_margin="5dp"
                android:background="@drawable/pen"
                android:alpha=".5"
                android:onClick="penButtonClicked" />
        </FrameLayout>

        <LinearLayout
            android:id="@+id/annotationFrame"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:visibility="invisible">
            <RelativeLayout
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                android:layout_weight="0.3">
            </RelativeLayout>
            <RelativeLayout
                android:id="@+id/innerAnnotationFrame"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                android:layout_weight="0.7">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">
                    <EditText
                        android:id="@+id/annotationText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:background="#ffffffff"
                        />
                </LinearLayout>
            </RelativeLayout>
        </LinearLayout>
    </FrameLayout>
   */
  private void initializeView() {
    // Add the map to the view layout
    viewLayout.addView(map, mapParams);

    // Layout for the hand drawn regions
    FrameLayout drawFrame = new FrameLayout(context);
                drawFrame.setLayoutParams(new FrameLayout.LayoutParams(
                         FrameLayout.LayoutParams.MATCH_PARENT,
                         FrameLayout.LayoutParams.MATCH_PARENT));
                drawFrame.setVisibility(handDrawnRegionsEnabled ? View.VISIBLE : View.INVISIBLE);
                setId(drawFrame, "drawFrame", generateViewId());

    // set up the pen button
    FrameLayout.LayoutParams penParams = new FrameLayout.LayoutParams(
                                       dpToPx(42),
                                       dpToPx(42),
                                       Gravity.BOTTOM | Gravity.RIGHT);
                             penParams.setMargins(0, 0, dpToPx(5), dpToPx(5)); // left, top, right, bottom

    Drawable pen = null;
    try {
      pen = MediaUtil.getBitmapDrawable(form, "res/drawable/pen");
    } catch (IOException e) {}

    final Button penButton = new Button(context);
           penButton.setBackground(pen);
           penButton.setAlpha(0.5f);
           penButton.setOnClickListener(new View.OnClickListener() {
             @Override public void onClick(View view) {
               penEnabled = !penEnabled;
               penButton.setAlpha(penEnabled ? 1f : 0.5f);
             }
           });
           penButton.setLayoutParams(penParams);
           setId(penButton, "penButton", generateViewId());

    drawFrame.addView(penButton);
    viewLayout.addView(drawFrame);

    // Annotation View
    LinearLayout annotationFrame = new LinearLayout(context);
                 annotationFrame.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT));
                 annotationFrame.setOrientation(LinearLayout.VERTICAL);
                 annotationFrame.setVisibility(View.INVISIBLE);
                 setId(annotationFrame, "annotationFrame", generateViewId());

    RelativeLayout fill = new RelativeLayout(context);
                   fill.setLayoutParams(new LinearLayout.LayoutParams(
                       LinearLayout.LayoutParams.FILL_PARENT,
                       LinearLayout.LayoutParams.FILL_PARENT,
                       0.3f));

    RelativeLayout innerAnnotationFrame = new RelativeLayout(context);
                   innerAnnotationFrame.setLayoutParams(new LinearLayout.LayoutParams(
                                       LinearLayout.LayoutParams.FILL_PARENT,
                                       LinearLayout.LayoutParams.FILL_PARENT,
                                       0.7f));
                   setId(innerAnnotationFrame, "innerAnnotationFrame", generateViewId());

    LinearLayout innerWrapper = new LinearLayout(context);
                 innerWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                             LinearLayout.LayoutParams.MATCH_PARENT,
                             LinearLayout.LayoutParams.MATCH_PARENT));
                 innerWrapper.setOrientation(LinearLayout.VERTICAL);

    EditText annotationText = new EditText(context);
             annotationText.setLayoutParams(new LinearLayout.LayoutParams(
                           LinearLayout.LayoutParams.MATCH_PARENT,
                           LinearLayout.LayoutParams.WRAP_CONTENT));
             annotationText.setBackgroundColor(0xffffffff);
             setId(annotationText, "annotationText", generateViewId());

    innerWrapper.addView(annotationText);
    innerAnnotationFrame.addView(innerWrapper);
    annotationFrame.addView(fill);
    annotationFrame.addView(innerAnnotationFrame);

    viewLayout.addView(annotationFrame);
  }

  private void setId(View view, String viewName, int id) {
    view.setId(id);
    viewIds.put(viewName, id);
  }

  private int getId(String viewName) {
    return viewIds.get(viewName);
  }

  private int dpToPx(int dp) {
    return (int) (dp * DISPLAY_DENSITY + 0.5f);
  }

  private void drawMarker(GeoPoint p) {
    Marker startMarker = new Marker(map, customResourceProxy);
    startMarker.setPosition(p);
    startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
    //startMarker.setIcon(Drawable.createFromPath("./osm_images/marker_default.png"));
    //startMarker.setInfoWindow(new AnnotationInfoWindow(R.layout.bonuspack_bubble, map));
    startMarker.setDraggable(true);
    markersOverlay.add(startMarker);
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
    zoomControlsEnabled = enable;
    map.setBuiltInZoomControls(zoomControlsEnabled);
    map.invalidate();
  }

  @SimpleProperty (description = "Gets zoom control settings")
  public boolean ZoomControls() {
    return zoomControlsEnabled;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
  @SimpleProperty (description = "Sets multi-touch controls")
  public void MultiTouchControls(Boolean enable) {
    multiTouchControlsEnabled = enable;
    map.setMultiTouchControls(multiTouchControlsEnabled);
    map.invalidate();
  }

  @SimpleProperty (description = "Gets multi-touch control settings")
  public boolean MultiTouchControls() {
    return multiTouchControlsEnabled;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
  @SimpleProperty (description = "Enables Markers")
  public void MarkersEnabled(Boolean enable) {
    markersEnabled = enable;
    markersOverlay.setEnabled(markersEnabled);
    map.invalidate();
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
    handDrawnRegionsOverlay.setEnabled(handDrawnRegionsEnabled);

    View view = viewLayout.findViewById(getId("drawFrame"));
    view.setVisibility(enable ? View.VISIBLE : View.INVISIBLE);
    map.invalidate();
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

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "False")
  @SimpleProperty (description = "Saves marker and hand drawn region annotations in a kml document on the phone")
  public void SaveAnnotationsInKMLEnabled(Boolean enable) {
    saveAnnotationsInKMLEnabled = enable;
  }

  @SimpleProperty (description = "Returns whether HandDrawnRegions are enabled")
  public boolean SaveAnnotationsInKMLEnabled() {
    return saveAnnotationsInKMLEnabled;
  }

  @DesignerProperty (editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
    defaultValue = "")
  @SimpleProperty (description = "Sets the kml file for saves if saving is enabled")
  public void KMLSaveFilePath(String filePath) {
    KMLFilePath = filePath;
  }

  @SimpleProperty (description = "Gets the kml file for saves if saving is enabled")
  public String KMLSaveFilePath() {
    return KMLFilePath;
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
