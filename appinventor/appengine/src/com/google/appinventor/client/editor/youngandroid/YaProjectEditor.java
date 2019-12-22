// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2019 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.editor.youngandroid;

import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.DesignToolbar;
import com.google.appinventor.client.ErrorReporter;
import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.boxes.AssetListBox;
import com.google.appinventor.client.editor.EditorManager;
import com.google.appinventor.client.editor.FileEditor;
import com.google.appinventor.client.editor.ProjectEditor;
import com.google.appinventor.client.editor.ProjectEditorFactory;
import com.google.appinventor.client.editor.simple.SimpleComponentDatabase;
import com.google.appinventor.client.editor.simple.components.MockComponent;
import com.google.appinventor.client.editor.simple.components.MockFusionTablesControl;
import com.google.appinventor.client.editor.youngandroid.i18n.BlocklyMsg;
import com.google.appinventor.client.explorer.dialogs.ProgressBarDialogBox;
import com.google.appinventor.client.explorer.project.ComponentDatabaseChangeListener;
import com.google.appinventor.client.explorer.project.Project;
import com.google.appinventor.client.explorer.project.ProjectChangeListener;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.client.properties.json.ClientJsonParser;
import com.google.appinventor.common.utils.StringUtils;
import com.google.appinventor.shared.properties.json.JSONArray;
import com.google.appinventor.shared.properties.json.JSONObject;
import com.google.appinventor.shared.properties.json.JSONValue;
import com.google.appinventor.shared.rpc.project.ChecksumedFileException;
import com.google.appinventor.shared.rpc.project.ChecksumedLoadFile;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.appinventor.shared.rpc.project.ProjectRootNode;
import com.google.appinventor.shared.rpc.project.SourceNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidComponentsFolder;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidFormNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidProjectNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidSourceNode;
import com.google.appinventor.shared.storage.StorageUtil;
import com.google.appinventor.shared.youngandroid.YoungAndroidSourceAnalyzer;
import com.google.common.collect.Maps;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.json.client.JSONException;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project editor for Young Android projects. Each instance corresponds to
 * one project that has been opened in this App Inventor session. 
 * Also responsible for managing screens list for this project in 
 * the DesignToolbar.
 *
 * @author lizlooney@google.com (Liz Looney)
 * @author sharon@google.com (Sharon Perl) - added logic for screens in  
 *     DesignToolbar
 */
public final class YaProjectEditor extends ProjectEditor implements ProjectChangeListener,
    ComponentDatabaseChangeListener {

  // FileEditors in a YA project come in sets. Every form in the project has 
  // a YaFormEditor for editing the UI, and a YaBlocksEditor for editing the 
  // blocks representation of the program logic. Some day it may also have an 
  // editor for the textual representation of the program logic.
  private static class EditorSet {
    YaFormEditor formEditor = null;
    YaBlocksEditor blocksEditor = null;
    YoungAndroidFormNode formNode = null;
    YoungAndroidBlocksNode blocksNode = null;
  }

  // Maps form name -> editors for this form
  private final HashMap<String, EditorSet> editorMap = Maps.newHashMap();
  
  // List of External Components
  private final List<String> externalComponents = new ArrayList<String>();

  // Mapping of package names to extensions defined by the package (n > 1)
  private final Map<String, Set<String>> externalCollections = new HashMap<>();
  private final Map<String, String> extensionToNodeName = new HashMap<>();
  private final Map<String, Set<String>> extensionsInNode = new HashMap<>();

  // Number of external component descriptors loaded since there is no longer a 1-1 correspondence
  private volatile int numExternalComponentsLoaded = 0;

  // List of ComponentDatabaseChangeListeners
  private final List<ComponentDatabaseChangeListener> componentDatabaseChangeListeners = new ArrayList<ComponentDatabaseChangeListener>();

  //State variables to help determine whether we are ready to load Project
  private boolean externalComponentsLoaded = false;

  // Database of component type descriptions
  private final SimpleComponentDatabase COMPONENT_DATABASE;

  private final Deque<EditorSet> loadingEditors = new LinkedList<>();

  // State variables to help determine whether we are ready to show Screen1  
  // Automatically select the Screen1 form editor when we have finished loading
  // both the form and blocks editors for Screen1 and we have added the 
  // screen to the DesignToolbar. Since the loading happens asynchronously,
  // there are multiple points when we may be ready to show the screen, and
  // we shouldn't try to show it before everything is ready.
  private boolean screen1FormLoaded = false;
  private boolean screen1BlocksLoaded = false;
  private boolean screen1Added = false;

  /**
   * Returns a project editor factory for {@code YaProjectEditor}s.
   *
   * @return a project editor factory for {@code YaProjectEditor}s.
   */
  public static ProjectEditorFactory getFactory() {
    return new ProjectEditorFactory() {
      @Override
      public ProjectEditor createProjectEditor(ProjectRootNode projectRootNode) {
        return new YaProjectEditor(projectRootNode);
      }
    };
  }

  public YaProjectEditor(ProjectRootNode projectRootNode) {
    super(projectRootNode);
    project.addProjectChangeListener(this);
    COMPONENT_DATABASE = SimpleComponentDatabase.getInstance(projectId);
  }

  private void loadBlocksEditor(String formNamePassedIn, final Runnable callback) {

    final String formName = formNamePassedIn;
    final EditorSet editors = editorMap.get(formName);
    final YaBlocksEditor newBlocksEditor = editors.blocksEditor;
    newBlocksEditor.loadFile(new AsyncCallback<ChecksumedLoadFile>() {
      @Override
      public void onFailure(Throwable throwable) {
        if (callback != null) {
          callback.run();
        }
      }

      @Override
        public void onSuccess(ChecksumedLoadFile file) {
          Ode.CLog("Loaded " + formName + ".bky");
          loadingEditors.remove(editors);
          YaBlocksEditor newBlocksEditor = editors.blocksEditor;
          int pos = Collections.binarySearch(fileIds, newBlocksEditor.getFileId(),
              getFileIdComparator());
          if (pos < 0) {
            pos = -pos - 1;
          }
          insertFileEditor(newBlocksEditor, pos);
          if (isScreen1(formName)) {
            screen1BlocksLoaded = true;
            if (readyToShowScreen1()) {
              Ode.CLog("YaProjectEditor.addBlocksEditor.loadFile.execute: switching to screen "
                  + formName + " for project " + newBlocksEditor.getProjectId());
              Ode.getInstance().getDesignToolbar().switchToScreen(newBlocksEditor.getProjectId(),
                  formName, DesignToolbar.View.FORM);
            }
          }
          if (callback != null) {
            callback.run();
          }
        }
      });

  }

  /**
   * Project process is completed before loadProject is started!
   * Currently Project process loads all External Components into Component Database
   */
  @Override
  public void processProject() {
    resetExternalComponents();
    resetProjectWarnings();
    loadExternalComponents();
    callLoadProject();
  }

  // Note: When we add the blocks editors in the loop below we do not actually
  // have them load the blocks file. Instead we trigger the load of a blocks file
  // in the callback for the loading of its associated forms file. This is important
  // because we have to ensure that the component type data is available when the
  // blocks are loaded!

  private EditorSet getOrCreateEditorSet(String formName) {
    EditorSet result = editorMap.get(formName);
    if (result == null) {
      result = new EditorSet();
      editorMap.put(formName, result);
    }
    return result;
  }

  private void loadProject() {
    final long start = System.currentTimeMillis();
    YoungAndroidFormNode screen1scm = null;
    YoungAndroidBlocksNode screen1bky = null;
    for (SourceNode source : projectRootNode.getAllSourceNodes()) {
      if (source instanceof YoungAndroidFormNode) {
        YoungAndroidFormNode form = (YoungAndroidFormNode) source;
        if (form.isScreen1()) {
          screen1scm = form;
        }
        getOrCreateEditorSet(form.getFormName()).formNode = form;
      } else if (source instanceof YoungAndroidBlocksNode) {
        YoungAndroidBlocksNode blocks = (YoungAndroidBlocksNode) source;
        if (blocks.isScreen1()) {
          screen1bky = blocks;
        }
        getOrCreateEditorSet(blocks.getFormName()).blocksNode = blocks;
      }
    }
    if (screen1scm == null || screen1bky == null) {
      Ode.getInstance().genericWarning(MESSAGES.screen1MissingText(project.getProjectName()));
      return;
    }

    final ProgressBarDialogBox progress = new ProgressBarDialogBox(MESSAGES.loadingAppIndicatorText(), projectRootNode);
    progress.show();
    progress.center();

    final DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    final Runnable callback = new Runnable() {
      int counter = 0;
      @Override
      public void run() {
        counter++;
        if (loadingEditors.isEmpty() && counter >= editorMap.size()) {
          // All done...
          progress.hide();
          designToolbar.sortScreenList(projectId);
          Ode.CLog("Loading project took " + (System.currentTimeMillis() - start) + " ms");
        } else {
          double timeRemaining = (double)(System.currentTimeMillis() - start) / counter *
              (editorMap.size() - counter) / 1000.0;
          progress.setProgress(100 * counter / editorMap.size(),
              MESSAGES.loadingScreen(loadingEditors.getFirst().formNode.getFormName(), (int) timeRemaining));
        }
      }
    };
    addFormEditor(screen1scm, callback);
    addBlocksEditor(screen1bky);

    final String screen1 = screen1scm.getFormName();
    EditorSet editors = editorMap.get(screen1);
    designToolbar.addScreen(projectRootNode.getProjectId(), screen1, editors.formEditor, editors.blocksEditor);
    screen1Added = true;
    if (readyToShowScreen1()) {
      Ode.CLog("YaProjectEditor.loadProject: switching to screen " + screen1
          + " for project " + projectRootNode.getProjectId());
      Ode.getInstance().getDesignToolbar().switchToScreen(projectRootNode.getProjectId(),
          screen1, DesignToolbar.View.FORM);
    }

    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      private Iterator<Map.Entry<String, EditorSet>> it = editorMap.entrySet().iterator();

      @Override
      public void execute() {
        if (it.hasNext()) {
          Map.Entry<String, EditorSet> toLoad = it.next();
          EditorSet editors = toLoad.getValue();
          if (!editors.formNode.isScreen1()) {  // Screen1 loading is already initialized
            if (editors.formNode == null) {
              Ode.getInstance().genericWarning(MESSAGES.screenMissingDesigner(project.getProjectName(), toLoad.getKey()));
            } else if (editors.blocksNode == null) {
              Ode.getInstance().genericWarning(MESSAGES.screenMissingBlocks(project.getProjectName(), toLoad.getKey()));
            } else {
              addFormEditor(editors.formNode, callback);
              addBlocksEditor(editors.blocksNode);
              DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
              designToolbar.addScreen(projectRootNode.getProjectId(),
                  toLoad.getKey(), editors.formEditor, editors.blocksEditor);
            }
          }
          Scheduler.get().scheduleDeferred(this);
        }
      }
    });
  }
  
  @Override
  protected void onShow() {
    OdeLog.log("YaProjectEditor got onShow() for project " + projectId);
    
    AssetListBox.getAssetListBox().getAssetList().refreshAssetList(projectId);
    
    DesignToolbar designToolbar = Ode.getInstance().getDesignToolbar();
    FileEditor selectedFileEditor = getSelectedFileEditor();
    if (selectedFileEditor != null) {
      if (selectedFileEditor instanceof YaFormEditor) {
        YaFormEditor formEditor = (YaFormEditor) selectedFileEditor;
        designToolbar.switchToScreen(projectId, formEditor.getForm().getName(), 
            DesignToolbar.View.FORM);
      } else if (selectedFileEditor instanceof YaBlocksEditor) {
        YaBlocksEditor blocksEditor = (YaBlocksEditor) selectedFileEditor;
        designToolbar.switchToScreen(projectId, blocksEditor.getForm().getName(), 
            DesignToolbar.View.BLOCKS);
      } else {
        // shouldn't happen!
        OdeLog.elog("YaProjectEditor got onShow when selectedFileEditor" 
            + " is not a form editor or a blocks editor!");
        ErrorReporter.reportError("Internal error: can't switch file editors.");
      }
    }
  }

  @Override
  protected void onHide() {
    OdeLog.log("YaProjectEditor: got onHide");
    AssetListBox.getAssetListBox().getAssetList().refreshAssetList(0);

    FileEditor selectedFileEditor = getSelectedFileEditor();
    if (selectedFileEditor != null) {
      selectedFileEditor.onHide();
    }
  }
  
  @Override
  protected void onUnload() {
    OdeLog.log("YaProjectEditor: got onUnload");
    super.onUnload();
    for (EditorSet editors : editorMap.values()) {
      editors.blocksEditor.prepareForUnload();
    }
  }

  // ProjectChangeListener methods

  @Override
  public void onProjectLoaded(Project project) {
  }

  @Override
  public void onProjectNodeAdded(Project project, ProjectNode node) {
    String formName = null;
    if (node instanceof YoungAndroidFormNode) {
      if (getFileEditor(node.getFileId()) == null) {
        addFormEditor((YoungAndroidFormNode) node, null);
        formName = ((YoungAndroidFormNode) node).getFormName();
      }
    } else if (node instanceof YoungAndroidBlocksNode) {
      if (getFileEditor(node.getFileId()) == null) {
        addBlocksEditor((YoungAndroidBlocksNode) node);
        formName = ((YoungAndroidBlocksNode) node).getFormName();
      }
    }
    if (formName != null) {
      // see if we have both editors yet
      EditorSet editors = editorMap.get(formName);
      if (editors.formEditor != null && editors.blocksEditor != null) {
        Ode.getInstance().getDesignToolbar().addScreen(node.getProjectId(), formName, 
            editors.formEditor, editors.blocksEditor);
      }
    }
  }


  @Override
  public void onProjectNodeRemoved(Project project, ProjectNode node) {
    // remove blocks and/or form editor if applicable. Remove screen from 
    // DesignToolbar. If the partner node to this one (blocks or form) was already 
    // removed, calling DesignToolbar.removeScreen a second time will be a no-op.
    OdeLog.log("YaProjectEditor: got onProjectNodeRemoved for project "
            + project.getProjectId() + ", node " + node.getFileId());
    String formName = null;
    if (node instanceof YoungAndroidFormNode) {
      formName = ((YoungAndroidFormNode) node).getFormName();
      removeFormEditor(formName);
    } else if (node instanceof YoungAndroidBlocksNode) {
      formName = ((YoungAndroidBlocksNode) node).getFormName();
      removeBlocksEditor(formName);
    }
  }
  
  /*
   * Returns the YaBlocksEditor for the given form name in this project
   */
  public YaBlocksEditor getBlocksFileEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      return editorMap.get(formName).blocksEditor;
    } else {
      return null;
    }
  }

  /* 
   * Returns the YaFormEditor for the given form name in this project
   */
  public YaFormEditor getFormFileEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      return editorMap.get(formName).formEditor;
    } else {
      return null;
    }
  }

  /**
   * @return a list of component instance names
   */
  public List<String> getComponentInstances(String formName) {
    List<String> components = new ArrayList<String>();
    EditorSet editorSet = editorMap.get(formName);
    if (editorSet == null) {
      return components;
    }
    components.addAll(editorSet.formEditor.getComponents().keySet());
    return  components;
  }

  public List<String> getComponentInstances() {
    List<String> components = new ArrayList<String>();
    for (String formName : editorMap.keySet()) {
      components.addAll(getComponentInstances(formName));
    }
    return components;
  }

  public Set<String> getComponentTypes(String formName) {
    Set<String> types = new HashSet<String>();
    EditorSet editorSet = editorMap.get(formName);
    if (editorSet == null) {
      return types;
    }
    for(MockComponent m : editorSet.formEditor.getComponents().values()) {
      types.add(m.getType());
    }
    return types;
  }

  public Set<String> getUniqueComponentTypes() {
    Set<String> types = new HashSet<String>();
    for (String formName : editorMap.keySet()) {
      types.addAll(getComponentTypes(formName));
    }
    return types;
  }

  public Set<String> getUniqueBuiltInBlockTypes() {
    Set<String> types = new HashSet<String>();
    for (EditorSet ed : editorMap.values()) {
      types.addAll(ed.blocksEditor.getBlockTypeSet());
    }
    return types;
  }

  // Returns a hash of component names with the set of all component blocks (events, methods,
  // and properties) in use for all screens in the current project
  public HashMap<String, Set<String>> getUniqueComponentBlockTypes() {
    HashMap<String, Set<String>> componentBlocks = new HashMap<String, Set<String>>();
    for (EditorSet ed : editorMap.values()) {
      componentBlocks = ed.blocksEditor.getComponentBlockTypeSet(componentBlocks);
    }
    return componentBlocks;
  }


  // Private methods

  private static Comparator<String> getFileIdComparator() {
    // File editors (YaFormEditors and YaBlocksEditors) are sorted so that Screen1 always comes
    // first and others are in alphabetical order. Within each pair, the YaFormEditor is
    // immediately before the YaBlocksEditor.
    return new Comparator<String>() {
      @Override
      public int compare(String fileId1, String fileId2) {
        boolean isForm1 = fileId1.endsWith(YoungAndroidSourceAnalyzer.FORM_PROPERTIES_EXTENSION);
        boolean isForm2 = fileId2.endsWith(YoungAndroidSourceAnalyzer.FORM_PROPERTIES_EXTENSION);

        // Give priority to screen1.
        if (YoungAndroidSourceNode.isScreen1(fileId1)) {
          if (YoungAndroidSourceNode.isScreen1(fileId2)) {
            // They are both named screen1. The form editor should come before the blocks editor.
            if (isForm1) {
              return isForm2 ? 0 : -1;
            } else {
              return isForm2 ? 1 : 0;
            }
          } else {
            // Only fileId1 is named screen1.
            return -1;
          }
        } else if (YoungAndroidSourceNode.isScreen1(fileId2)) {
          // Only fileId2 is named screen1.
          return 1;
        }

        String fileId1WithoutExtension = StorageUtil.trimOffExtension(fileId1);
        String fileId2WithoutExtension = StorageUtil.trimOffExtension(fileId2);
        int compare = fileId1WithoutExtension.compareTo(fileId2WithoutExtension);
        if (compare != 0) {
          return compare;
        }
        // They are both the same name without extension. The form editor should come before the
        // blocks editor.
        if (isForm1) {
          return isForm2 ? 0 : -1;
        } else {
          return isForm2 ? 1 : 0;
        }
      }
    };
  }
  
  private void addFormEditor(YoungAndroidFormNode formNode, final Runnable callback) {
    final long start = System.currentTimeMillis();
    final YaFormEditor newFormEditor = new YaFormEditor(this, formNode);
    final String formName = formNode.getFormName();
    if (editorMap.containsKey(formName)) {
      // This happens if the blocks editor was already added.
      editorMap.get(formName).formEditor = newFormEditor;
    } else {
      EditorSet editors = new EditorSet();
      editors.formEditor = newFormEditor;
      editorMap.put(formName, editors);
    }
    final AsyncCallback<ChecksumedLoadFile> handler = new AsyncCallback<ChecksumedLoadFile>() {
      @Override
      public void onFailure(Throwable throwable) {
        if (callback != null) {
          callback.run();
        }
      }

      @Override
      public void onSuccess(ChecksumedLoadFile checksumedLoadFile) {
        Ode.CLog("Loaded " + formName + ".scm");
        int pos = Collections.binarySearch(fileIds, newFormEditor.getFileId(),
            getFileIdComparator());
        if (pos < 0) {
          pos = -pos - 1;
        }
        insertFileEditor(newFormEditor, pos);
        if (isScreen1(formName)) {
          screen1FormLoaded = true;
          if (readyToShowScreen1()) {
            Ode.CLog("YaProjectEditor.addFormEditor.loadFile.execute: switching to screen "
                + formName + " for project " + newFormEditor.getProjectId());
            Ode.getInstance().getDesignToolbar().switchToScreen(newFormEditor.getProjectId(),
                formName, DesignToolbar.View.FORM);
          }
        }
        loadBlocksEditor(formName, callback);
      }
    };
    if (!isScreen1(formName) && !screen1FormLoaded) {
      // Defer loading other screens until Screen1 is loaded. Otherwise we can end up in an
      // inconsistent state during project upgrades with Screen1-only properties.
      Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {
        @Override
        public boolean execute() {
          if (screen1FormLoaded) {
            loadingEditors.add(editorMap.get(formName));
            newFormEditor.loadFile(handler);
            return false;
          } else {
            return true;
          }
        }
      }, 100);
    } else {
      loadingEditors.add(editorMap.get(formName));
      newFormEditor.loadFile(handler);
    }
    Ode.CLog("Adding form editor for " + formName + " took " + (System.currentTimeMillis() - start) + " ms");
  }
    
  private boolean readyToShowScreen1() {
    return screen1FormLoaded && screen1BlocksLoaded && screen1Added;
  }

  private boolean readyToLoadProject() {
    return BlocklyMsg.Loader.isTranslationLoaded() && externalComponentsLoaded;
  }

  private void addBlocksEditor(YoungAndroidBlocksNode blocksNode) {
    final long start = System.currentTimeMillis();
    final YaBlocksEditor newBlocksEditor = new YaBlocksEditor(this, blocksNode);
    final String formName = blocksNode.getFormName();
    if (editorMap.containsKey(formName)) {
      // This happens if the form editor was already added.
      editorMap.get(formName).blocksEditor = newBlocksEditor;
    } else {
      EditorSet editors = new EditorSet();
      editors.blocksEditor = newBlocksEditor;
      editorMap.put(formName, editors);
    }
    Ode.CLog("Adding blocks editor for " + formName + " took " + (System.currentTimeMillis() - start) + " ms");
  }
  
  private void removeFormEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      EditorSet editors = editorMap.get(formName);
      if (editors.blocksEditor == null) {
        editorMap.remove(formName);
      } else {
        editors.formEditor = null;
      }
    }
  }
  
  private void removeBlocksEditor(String formName) {
    if (editorMap.containsKey(formName)) {
      EditorSet editors = editorMap.get(formName);
      if (editors.formEditor == null) {
        editorMap.remove(formName);
      } else {
        editors.blocksEditor = null;
      }
    }    
  }
  
  public void addComponent(final ProjectNode node, final Command afterComponentAdded) {
    final String fileId = node.getFileId();
    AsyncCallback<ChecksumedLoadFile> callback = new OdeAsyncCallback<ChecksumedLoadFile>(MESSAGES.loadError()) {
      @Override
      public void onSuccess(ChecksumedLoadFile result) {
        String jsonFileContent;
        try {
          jsonFileContent = result.getContent();
        } catch (ChecksumedFileException e) {
          this.onFailure(e);
          return;
        }
        JSONValue value = null;
        try {
          value = new ClientJsonParser().parse(jsonFileContent);
        } catch(JSONException e) {
          // thrown if jsonFileContent is not valid JSON
          String[] parts = fileId.split("/");
          if (parts.length > 3 && fileId.endsWith("components.json")) {
            ErrorReporter.reportError(Ode.MESSAGES.extensionDescriptorCorrupt(parts[2], project.getProjectName()));
          } else {
            ErrorReporter.reportError(Ode.MESSAGES.invalidExtensionInProject(project.getProjectName()));
          }
          numExternalComponentsLoaded++;
          if (afterComponentAdded != null) {
            afterComponentAdded.execute();
          }
          return;
        }
        COMPONENT_DATABASE.addComponentDatabaseListener(YaProjectEditor.this);
        if (value instanceof JSONArray) {
          JSONArray componentList = value.asArray();
          COMPONENT_DATABASE.addComponents(componentList);
          for (JSONValue component : componentList.getElements()) {
            String name = component.asObject().get("type").asString().getString();
            // group new extensions by package name
            String packageName = name.substring(0, name.lastIndexOf('.'));
            if (!externalCollections.containsKey(packageName)) {
              externalCollections.put(packageName, new HashSet<String>());
            }
            externalCollections.get(packageName).add(name);

            if (!extensionsInNode.containsKey(fileId)) {
              extensionsInNode.put(fileId, new HashSet<String>());
            }
            extensionsInNode.get(fileId).add(name);
            extensionToNodeName.put(name, fileId);

            name = packageName;
            if (!externalComponents.contains(name)) {
              externalComponents.add(name);
            } else {
              // Upgraded an extension. Force a save to ensure version numbers are updated serverside.
              saveProject();
            }
          }
        } else {
          JSONObject componentJSONObject = value.asObject();
          COMPONENT_DATABASE.addComponent(componentJSONObject);
          // In case of upgrade, we do not need to add entry
          if (!externalComponents.contains(componentJSONObject.get("type").toString())) {
            externalComponents.add(componentJSONObject.get("type").toString());
          } else {
            // Upgraded an extension. Force a save to ensure version numbers are updated serverside.
            saveProject();
          }
        }
        numExternalComponentsLoaded++;
        if (afterComponentAdded != null) {
          afterComponentAdded.execute();
        }
      }
      @Override
      public void onFailure(Throwable caught) {
        if (caught instanceof ChecksumedFileException) {
          Ode.getInstance().recordCorruptProject(projectId, fileId, caught.getMessage());
        }
        super.onFailure(caught);
      }
    };
    Ode.getInstance().getProjectService().load2(projectId, fileId, callback);
  }

  /**
   * To remove Component Files from the Project!
   * @param componentTypes
   */
  public  void removeComponent(Map<String, String> componentTypes) {
    final Ode ode = Ode.getInstance();
    final YoungAndroidComponentsFolder componentsFolder = ((YoungAndroidProjectNode) project.getRootNode()).getComponentsFolder();
    Set<String> externalCompFolders = new HashSet<String>();
    // Old projects with old extensions will use FQCN for the directory name rather than the package
    // Prefer deleting com.foo.Bar over com.foo if com.foo.Bar exists.
    for (ProjectNode child : componentsFolder.getChildren()) {
      String[] parts = child.getFileId().split("/");
      if (parts.length >= 3) {
        externalCompFolders.add(parts[2]);
      }
    }
    Set<String> removedPackages = new HashSet<String>();
    for (String componentType : componentTypes.keySet()) {
      String typeName = componentTypes.get(componentType);
      if (!externalCompFolders.contains(typeName) && !externalComponents.contains(typeName)) {
        typeName = typeName.substring(0, typeName.lastIndexOf('.'));
        if (removedPackages.contains(typeName)) {
          continue;
        }
        removedPackages.add(typeName);
      }
      final String directory = componentsFolder.getFileId() + "/" + typeName + "/";
      ode.getProjectService().deleteFolder(ode.getSessionId(), this.projectId, directory,
          new AsyncCallback<Long>() {
            @Override
            public void onFailure(Throwable throwable) {

            }
            @Override
            public void onSuccess(Long date) {
              Iterable<ProjectNode> nodes = componentsFolder.getChildren();
              for (ProjectNode node : nodes) {
                if (node.getFileId().startsWith(directory)) {
                  ode.getProjectManager().getProject(node).deleteNode(node);
                  ode.updateModificationDate(node.getProjectId(), date);
                }
              }
              // Change in extensions requires companion refresh
              YaBlocksEditor.resendExtensionsList();
            }
          });
    }
  }

  private void callLoadProject() {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        if (!readyToLoadProject()) { // wait till project is processed
          Scheduler.get().scheduleDeferred(this);
        } else {
          loadProject();
        }
      }
    });
  }

  private void loadExternalComponents() {
    //Get the list of all ComponentNodes to be Added
    List<ProjectNode> componentNodes = new ArrayList<ProjectNode>();
    YoungAndroidComponentsFolder componentsFolder = ((YoungAndroidProjectNode) project.getRootNode()).getComponentsFolder();
    if (componentsFolder != null) {
      for (ProjectNode node : componentsFolder.getChildren()) {
        // Find all components that are json files.
        final String nodeName = node.getName();
        if (nodeName.endsWith(".json") && StringUtils.countMatches(node.getFileId(), "/") == 3 ) {
          componentNodes.add(node);
        }
      }
    }
    final int componentCount = componentNodes.size();
    for (ProjectNode componentNode : componentNodes) {
      addComponent(componentNode, new Command() {
        @Override
        public void execute() {
          if (componentCount == numExternalComponentsLoaded) { // true for the last component added
            externalComponentsLoaded = true;
          }
        }
      });
    }
    if (componentCount == 0) {
      externalComponentsLoaded = true; // to hint that we are ready to load
    }
  }

  // Resets any warnings that should be given when a project is loaded
  // For now this is just the deprecation warning for the
  // FusiontablesControl component.

  private void resetProjectWarnings() {
    MockFusionTablesControl.resetWarning();
  }

  private void resetExternalComponents() {
    COMPONENT_DATABASE.addComponentDatabaseListener(this);
    try {
      COMPONENT_DATABASE.resetDatabase();
    } catch(JSONException e) {
      // thrown if any of the component/extension descriptions are not valid JSON
      ErrorReporter.reportError(Ode.MESSAGES.componentDatabaseCorrupt(project.getProjectName()));
    }
    externalComponents.clear();
    extensionsInNode.clear();
    extensionToNodeName.clear();
    numExternalComponentsLoaded = 0;
  }

  private static boolean isScreen1(String formName) {
    return formName.equals(YoungAndroidSourceNode.SCREEN1_FORM_NAME);
  }

  public void addComponentDatbaseListener(ComponentDatabaseChangeListener cdbChangeListener) {
    componentDatabaseChangeListeners.add(cdbChangeListener);
  }

  public void removeComponentDatbaseListener(ComponentDatabaseChangeListener cdbChangeListener) {
    componentDatabaseChangeListeners.remove(cdbChangeListener);
  }

  public void clearComponentDatabaseListeners() {
    componentDatabaseChangeListeners.clear();
  }

  @Override
  public void onComponentTypeAdded(List<String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeAdded(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.formEditor.onComponentTypeAdded(componentTypes);
      editors.blocksEditor.onComponentTypeAdded(componentTypes);
    }
    // Change of extensions...
    YaBlocksEditor.resendAssetsAndExtensions();
  }

  @Override
  public boolean beforeComponentTypeRemoved(List<String> componentTypes) {
    boolean result = true;
    Set<String> removedTypes = new HashSet<>(componentTypes);
    // aggregate types in the same package
    for (String type : removedTypes) {
      Set<String> siblings = extensionsInNode.get(extensionToNodeName.get(COMPONENT_DATABASE.getComponentType(type)));
      if (siblings != null) {
        for (String siblingType : siblings) {
          String siblingName = siblingType.substring(siblingType.lastIndexOf('.') + 1);
          if (!removedTypes.contains(siblingName)) {
            componentTypes.add(siblingName);
          }
        }
      }
    }
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      result = result & cdbChangeListener.beforeComponentTypeRemoved(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      result = result & editors.formEditor.beforeComponentTypeRemoved(componentTypes);
      result = result & editors.blocksEditor.beforeComponentTypeRemoved(componentTypes);
    }
    return result;
  }

  @Override
  public void onComponentTypeRemoved(Map<String, String> componentTypes) {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onComponentTypeRemoved(componentTypes);
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.formEditor.onComponentTypeRemoved(componentTypes);
      editors.blocksEditor.onComponentTypeRemoved(componentTypes);
    }
    removeComponent(componentTypes);
  }

  @Override
  public void onResetDatabase() {
    COMPONENT_DATABASE.removeComponentDatabaseListener(this);
    for (ComponentDatabaseChangeListener cdbChangeListener : componentDatabaseChangeListeners) {
      cdbChangeListener.onResetDatabase();
    }
    for (String formName : editorMap.keySet()) {
      EditorSet editors = editorMap.get(formName);
      editors.formEditor.onResetDatabase();
      editors.blocksEditor.onResetDatabase();
    }
  }

  /**
   * Save all editors in the project.
   */
  public void saveProject() {
    EditorManager manager = Ode.getInstance().getEditorManager();
    for (EditorSet editors : editorMap.values()) {
      // It would be more efficient to check if the editors use the component in question,
      // but we are conservative and save everything, for now.
      manager.scheduleAutoSave(editors.formEditor);
      manager.scheduleAutoSave(editors.blocksEditor);
    }
  }
}
