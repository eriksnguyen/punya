// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0
package com.google.appinventor.buildserver;

public class OpenStreetMapXmlConstants {

  private OpenStreetMapXmlConstants() {
  }

  public final static String OSM_MAP =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n\n" +
        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
        "    android:orientation=\"vertical\"\n" +
        "    android:layout_width=\"fill_parent\"\n" +
        "    android:layout_height=\"fill_parent\">\n" +
        "    <org.osmdroid.views.MapView android:id=\"@+id/map\"\n" +
        "        android:layout_width=\"fill_parent\"\n" +
        "        android:layout_height=\"fill_parent\" />\n" +
        "    <FrameLayout\n" +
        "        android:id=\"@+id/drawFrame\"\n" +
        "        android:layout_width=\"match_parent\"\n" +
        "        android:layout_height=\"match_parent\">\n" +
        "        <Button\n" +
        "            android:id=\"@+id/penButton\"\n" +
        "            android:layout_height=\"42dp\"\n" +
        "            android:layout_width=\"42dp\"\n" +
        "            android:layout_gravity=\"bottom|right\"\n" +
        "            android:layout_margin=\"5dp\"\n" +
        "            android:background=\"@drawable/pen\"\n" +
        "            android:alpha=\".5\"\n" +
        "            android:onClick=\"penButtonClicked\" />\n" +
        "    </FrameLayout>\n" +
        "    <LinearLayout\n" +
        "        android:id=\"@+id/annotationFrame\"\n" +
        "        android:layout_width=\"match_parent\"\n" +
        "        android:layout_height=\"match_parent\"\n" +
        "        android:orientation=\"vertical\"\n" +
        "        android:visibility=\"invisible\">\n" +
        "        <RelativeLayout\n" +
        "            android:layout_width=\"fill_parent\"\n" +
        "            android:layout_height=\"fill_parent\"\n" +
        "            android:layout_weight=\"0.3\">\n" +
        "        </RelativeLayout>\n" +
        "        <RelativeLayout\n" +
        "            android:id=\"@+id/innerAnnotationFrame\"\n" +
        "            android:layout_width=\"fill_parent\"\n" +
        "            android:layout_height=\"fill_parent\"\n" +
        "            android:layout_weight=\"0.7\">\n" +
        "            <LinearLayout\n" +
        "                android:layout_width=\"match_parent\"\n" +
        "                android:layout_height=\"match_parent\"\n" +
        "                android:orientation=\"vertical\">\n" +
        "                <EditText\n" +
        "                    android:id=\"@+id/annotationText\"\n" +
        "                    android:layout_width=\"match_parent\"\n" +
        "                    android:layout_height=\"wrap_content\"\n" +
        "                    android:background=\"#ffffffff\"\n" +
        "                    />\n" +
        "            </LinearLayout>\n" +
        "        </RelativeLayout>\n" +
        "    </LinearLayout>\n" +
        "</FrameLayout>";

  public final static String BONUSPACK_BUBBLE =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:id=\"@+id/bubble_layout\"\n" +
        "    android:layout_width=\"wrap_content\"\n" +
        "    android:layout_height=\"wrap_content\"\n" +
        "    android:orientation=\"horizontal\"\n" +
        "    android:background=\"@drawable/bonuspack_bubble\">\n" +
        "    <LinearLayout\n" +
        "        android:layout_width=\"wrap_content\"\n" +
        "        android:layout_height=\"wrap_content\"\n" +
        "        android:paddingLeft=\"5dp\"\n" +
        "        android:orientation=\"vertical\" >\n" +
        "        <TextView android:id=\"@+id/bubble_description\"\n" +
        "            android:layout_width=\"wrap_content\"\n" +
        "            android:layout_height=\"wrap_content\"\n" +
        "            android:textColor=\"#000000\"\n" +
        "            android:textSize=\"12sp\"\n" +
        "            android:maxEms=\"17\"\n" +
        "            android:text=\"Description\" />\n" +
        "    </LinearLayout>\n" +
        "</LinearLayout>";
}
