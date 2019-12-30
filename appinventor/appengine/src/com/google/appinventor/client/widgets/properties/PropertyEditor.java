// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2019 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.widgets.properties;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Composite;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Superclass for all property editors.
 *
 */
public abstract class PropertyEditor extends Composite {

  // A list of editors that need to be updated on the next pass.
  private static List<PropertyEditor> editorsPendingUpdate = new LinkedList<>();

  // A pending update command, if any
  private static Scheduler.ScheduledCommand pendingUpdate = null;

  /**
   * Flag to indicate that the property editor is being used to edit multiple components.
   */
  private boolean multiselectMode = false;

  /**
   * Flag to indicate that the property editor's value is indeterminant due to conflicting values
   * for multiple selected components.
   */
  private boolean multiple = false;

  /**
   * Property being edited by this editor.
   */
  protected EditableProperty property;

  /**
   * Updates the property value shown in the editor.
   */
  protected abstract void updateValue();

  /**
   * Sets the property to be edited by this editor.
   *
   * @param property  property to be edited by this editor
   */
  public void setProperty(EditableProperty property) {
    this.property = property;
    property.setEditor(this);
    scheduleUpdateValue(this);
  }

  /**
   * Called when this property editor is being orphaned.
   *
   * <p>If a property editor listens for events, it should override this method so
   * it will stop listening for events after it has been orphaned.
   */
  public void orphan() {
  }

  public void refresh() {
    this.updateValue();
  }

  /**
   * Sets whether the property editor is being used to edit multiple components.
   *
   * @param multiselect true if the editor is editing multiple properties, otherwise false.
   */
  public void setMultiselectMode(boolean multiselect) {
    multiselectMode = multiselect;
  }

  /**
   * Checks whether the editor is in multiple select mode.
   *
   * @return true if the editor is being used to edit multiple components, otherwise false.
   */
  public boolean inMultiselectMode() {
    return multiselectMode;
  }

  /**
   * Sets whether the property editor is editing multiple components with conflicting values.
   *
   * @param multiple true if the properties are in conflict, otherwise false.
   */
  public void setMultipleValues(boolean multiple) {
    this.multiple = multiple;
  }

  /**
   * Checks whether the editor is editing multiple conflicting values.
   *
   * @return true if there is a conflict, otherwise false.
   */
  public boolean isMultipleValues() {
    return multiple;
  }

  private static void scheduleUpdateValue(final PropertyEditor editor) {
    editorsPendingUpdate.add(editor);
    if (pendingUpdate != null) {
      return;
    }
    pendingUpdate = new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        List<PropertyEditor> editors = new ArrayList<>(editorsPendingUpdate);
        editorsPendingUpdate.clear();
        pendingUpdate = null;
        for (PropertyEditor editor : editors) {
          editor.updateValue();
        }
      }
    };
    Scheduler.get().scheduleDeferred(pendingUpdate);
  }
}
