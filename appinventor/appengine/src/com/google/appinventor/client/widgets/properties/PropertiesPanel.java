// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.widgets.properties;

import com.google.appinventor.client.editor.simple.components.utils.PropertiesUtil;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

import java.util.List;
import java.util.Map;

/**
 * Panel to display properties.
 *
 */
public class PropertiesPanel extends Composite implements ComponentDatabaseChangeListener {

  private static final PropertiesPanel INSTANCE = new PropertiesPanel();

  // UI elements
  private final VerticalPanel panel;
  private final Label componentName;

  public static PropertiesPanel get() {
    return INSTANCE;
  }

  /**
   * Creates a new properties panel.
   */
  private PropertiesPanel() {
    // Initialize UI
    VerticalPanel outerPanel = new VerticalPanel();
    outerPanel.setWidth("100%");

    componentName = new Label("");
    componentName.setStyleName("ode-PropertiesComponentName");
    outerPanel.add(componentName);

    panel = new VerticalPanel();
    panel.setWidth("100%");
    panel.setStylePrimaryName("ode-PropertiesPanel");
    outerPanel.add(panel);

    initWidget(outerPanel);

    this.setSize("100%", "!00%");
  }

  /**
   * Adds a new property to be displayed in the UI.
   *
   * @param property  new property to be shown
   */
  void addProperty(EditableProperty property) {
    Label label = new Label(property.getCaption());
    label.setStyleName("ode-PropertyLabel");
    panel.add(label);
    PropertyEditor editor = PropertiesUtil.getPropertyEditor(property);
    // Since UIObject#setStyleName(String) clears existing styles, only
    // style the editor if it hasn't already been styled during instantiation.
    if (!editor.getStyleName().contains("PropertyEditor")) {
      editor.setStyleName("ode-PropertyEditor");
    }
    panel.add(editor);
  }

  /**
   * Removes all properties from the properties panel.
   */
  public void clear() {
    panel.clear();
    componentName.setText("");
    PropertiesUtil.willRefreshProperties();
  }

  /**
   * Shows a set of properties in the panel. Any previous content will be
   * removed.
   *
   * @param properties  properties to be shown
   */
  public void setProperties(EditableProperties properties) {
    clear();
    properties.addToPropertiesPanel(this);
  }

  /**
   * Set the label at the top of the properties panel. Note that you have
   * to do this after calling setProperties because it clears the label!
   * @param name The text to be shown above the properties.
   */
  public void setPropertiesCaption(String name) {
    componentName.setText(name);
  }

  @Override
  public void onComponentTypeAdded(List<String> componentTypes) {

  }

  @Override
  public boolean beforeComponentTypeRemoved(List<String> componentTypes) {
    return true;
  }

  @Override
  public void onComponentTypeRemoved(Map<String, String> componentTypes) {

  }

  @Override
  public void onResetDatabase() {

  }
}
