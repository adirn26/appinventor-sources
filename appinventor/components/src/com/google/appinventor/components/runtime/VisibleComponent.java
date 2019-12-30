// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;

/**
 * Superclass of visible components in the runtime libraries.
 * <p>
 * Defines standard properties and events.
 *
 */
@SimpleObject
public abstract class VisibleComponent implements Component {
  protected VisibleComponent() {
  }

  /**
   * Width property getter method.
   *
   * @return  width property used by the layout
   */
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public abstract int Width();

  /**
   * Width property setter method.
   *
   * @param width  width property used by the layout
   */
  @SimpleProperty
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_X_DIM_MEASURE,
      defaultValue = LENGTH_PREFERRED + "")
  public abstract void Width(int width);

  /**
   * Width property setter method.
   *
   * This version takes a percentage of the parent
   * component's width as its input.
   *
   * @param wPercent width as a percent of its parent
   *
   */
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public abstract void WidthPercent(int wPercent);

  /**
   * Height property getter method.
   *
   * @return  height property used by the layout
   */
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public abstract int Height();

  /**
   * Height property setter method.
   *
   * @param height  height property used by the layout
   */
  @SimpleProperty
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_Y_DIM_MEASURE,
      defaultValue = LENGTH_PREFERRED + "")
  public abstract void Height(int height);

  /**
   * Height property setter method.
   *
   * This version takes a percentage of the parent
   * component's height as its input.
   *
   * @param hPercent width as a percent of the height of its parent
   *
   */
  @SimpleProperty(
      category = PropertyCategory.APPEARANCE)
  public abstract void HeightPercent(int hPercent);
}
