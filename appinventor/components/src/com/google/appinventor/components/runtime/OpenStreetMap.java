// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import java.io.File;
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
@UsesLibraries(libraries = "slf4j-android.jar,osmdroid-android.jar") // SLF4J not req for osmdroid v 5.0+
public class OpenStreetMap extends AndroidViewComponent {

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
    context = container.$context();
    form = container.$form();
    savedInstanceState = form.getOnCreateBundle();

    // try raw mapView with in the fragmment
    viewLayout = new android.widget.LinearLayout(context);
    viewLayout.setId(generateViewId());

    container.$add(this);

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

  @Override
  public View getView() {
    return viewLayout;
  }
}
