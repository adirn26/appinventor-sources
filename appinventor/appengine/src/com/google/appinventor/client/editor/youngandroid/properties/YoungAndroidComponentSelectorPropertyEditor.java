// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.youngandroid.properties;

import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.Ode;
import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.simple.components.FormChangeListener;
import com.google.appinventor.client.editor.simple.components.MockComponent;
import com.google.appinventor.client.editor.youngandroid.YaBlocksEditor;
import com.google.appinventor.client.editor.youngandroid.YaFormEditor;
import com.google.appinventor.client.editor.youngandroid.YaProjectEditor;
import com.google.appinventor.client.widgets.properties.AdditionalChoicePropertyEditor;
import com.google.appinventor.client.widgets.properties.EditableProperty;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import java.util.Set;

/**
 * Property editor for selecting a component for a property.
 *
 * @author lizlooney@google.com (Liz Looney)
 */
public final class YoungAndroidComponentSelectorPropertyEditor
    extends AdditionalChoicePropertyEditor implements FormChangeListener {
  // UI elements
  private final ListBox componentsList;

  private final ListWithNone choices;

  // The types of component that can be chosen
  private final Set<String> componentTypes;

  // The YaFormEditor currently associated with this property editor.
  private YaFormEditor currentFormEditor = null;

  /**
   * Creates a new property editor for selecting a component.
   */
  public YoungAndroidComponentSelectorPropertyEditor() {
    this(null);
  }

  /**
   * Creates a new property editor for selecting a component, where the
   * user chooses among components of one or more component types.
   *
   * @param componentTypes types of component that can be selected, or null if
   *        all types of components can be selected.
   */
  public YoungAndroidComponentSelectorPropertyEditor(Set<String> componentTypes) {
    this.componentTypes = componentTypes;

    VerticalPanel selectorPanel = new VerticalPanel();
    componentsList = new ListBox();
    componentsList.setVisibleItemCount(10);
    componentsList.setWidth("100%");
    componentsList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        setOkButtonEnabled(true);
      }
    });
    selectorPanel.add(componentsList);
    selectorPanel.setWidth("100%");

    choices = new ListWithNone(MESSAGES.noneCaption(), new ListWithNone.ListBoxWrapper() {
      @Override
      public void addItem(String item) {
        componentsList.addItem(item);
      }

      @Override
      public String getItem(int index) {
        return componentsList.getItemText(index);
      }

      @Override
      public void removeItem(int index) {
        componentsList.removeItem(index);
      }

      @Override
      public void setSelectedIndex(int index) {
        componentsList.setSelectedIndex(index);
      }

      @Override
      public void clear() {
        componentsList.clear();
      }
    });

    initAdditionalChoicePanel(selectorPanel);
  }

  private void finishInitialization() {
    // Previous version had a bug where the value could be accidentally saved as "None".
    // If the property value is "None" and choices doesn't contain the value "None", set the
    // property value to "".
    String value = property.getValue();
    if (value.equals("None") && !choices.containsValue(value)) {
      property.setValue("");
    }
  }

  @Override
  public void setProperty(EditableProperty property) {
    super.setProperty(property);
    FileEditor activeEditor = Ode.getInstance().getCurrentFileEditor();
    YaFormEditor newEditor = null;
    if (activeEditor instanceof YaFormEditor) {
      newEditor = (YaFormEditor) activeEditor;
    } else if (activeEditor instanceof YaBlocksEditor) {
      YaProjectEditor yaProjectEditor = (YaProjectEditor) activeEditor.getProjectEditor();
      newEditor = yaProjectEditor.getFormFileEditor(((YoungAndroidBlocksNode) activeEditor.getFileNode()).getFormName());
    } else {
      throw new IllegalStateException();
    }
    if (newEditor == currentFormEditor) {
      return;
    }
    if (currentFormEditor != null) {
      currentFormEditor.getForm().removeFormChangeListener(this);
    }
    newEditor.getForm().addFormChangeListener(this);
    currentFormEditor = newEditor;
    reloadList();
  }

  private void reloadList() {
    choices.clear();

    // Fill choices with the components.
    for (MockComponent component : currentFormEditor.getComponents().values()) {
      if (componentTypes == null || componentTypes.contains(component.getType())) {
        choices.addItem(component.getName());
      }
    }

    finishInitialization();
  }

  @Override
  public void orphan() {
    currentFormEditor.getForm().removeFormChangeListener(this);
    super.orphan();
  }

  @Override
  protected void openAdditionalChoiceDialog() {
    if (!isMultipleValues()) {
      choices.selectValue(property.getValue());
    } else {
      setOkButtonEnabled(false);
    }
    super.openAdditionalChoiceDialog();
    componentsList.setFocus(true);
  }

  @Override
  protected String getPropertyValueSummary() {
    if (isMultipleValues()) {
      return MESSAGES.multipleValues();
    }
    String value = property.getValue();
    if (choices.containsValue(value)) {
      return choices.getDisplayItemForValue(value);
    }
    return value;
  }

  @Override
  protected boolean okAction() {
    int selected = componentsList.getSelectedIndex();
    if (selected == -1) {
      Window.alert(MESSAGES.noComponentSelected());
      return false;
    }
    boolean multiple = isMultipleValues();
    setMultipleValues(false);
    property.setValue(choices.getValueAtIndex(selected), multiple);
    return true;
  }

  // FormChangeListener

  public void onComponentPropertyChanged(MockComponent component,
      String propertyName, String propertyValue) {
  }

  public void onComponentRemoved(MockComponent component, boolean permanentlyDeleted) {
    if (permanentlyDeleted) {
      if (componentTypes == null || componentTypes.contains(component.getType())) {
        String componentName = component.getName();

        // Check whether our component was removed.
        String currentValue = property.getValue();
        if (componentName.equals(currentValue)) {
          // Our component was removed.
          property.setValue("");
        }

        // Remove the component from the list.
        choices.removeValue(componentName);
      }
    }
  }

  public void onComponentAdded(MockComponent component) {
    if (componentTypes == null || componentTypes.contains(component.getType())) {
      choices.addItem(component.getName());
    }
  }

  public void onComponentRenamed(MockComponent component, String oldName) {
    if (componentTypes == null || componentTypes.contains(component.getType())) {
      String newName = component.getName();

      // Add the new name to the list.
      choices.addItem(newName);

      // Check whether our component was renamed.
      String currentValue = property.getValue();
      if (oldName.equals(currentValue)) {
        // Our component was renamed.
        property.setValue(newName);
      }

      // Remove the old name from the choices.
      choices.removeValue(oldName);
    }
  }

  public void onComponentSelectionChange(MockComponent component, boolean selected) {
  }
}
