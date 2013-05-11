/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.model.change;

import java.util.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.server.rest.request.VcsRootInstanceRequest;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.*;
import jetbrains.vcs.api.VcsSettings;
import jetbrains.vcs.api.services.tc.MappingGeneratorService;
import jetbrains.vcs.api.services.tc.VcsMappingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root")
@XmlType(name = "vcs-root", propOrder = { "id", "internalId", "name","vcsName", "modificationCheckInterval", "status", "lastChecked",
  "project", "properties", "vcsRootInstances"})
@SuppressWarnings("PublicField")
public class VcsRoot {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public Long internalId;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String vcsName;

  @XmlAttribute
  public Integer modificationCheckInterval;

  @XmlAttribute
  public String status;

  @XmlAttribute
  public String lastChecked;


  @XmlElement
  public Properties properties;

  /**
   * Used only when creating new VCS roots
   * @deprecated Specify project element instead
   */
  @XmlAttribute
  public String projectLocator;

  @XmlElement(name = "project")
  public ProjectRef project;

  @XmlElement
  public Href vcsRootInstances;

  /*
  @XmlAttribute
  private String currentVersion;
  */

  public VcsRoot() {
  }

  public VcsRoot(final SVcsRoot root, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    id = root.getExternalId();
    internalId =  TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? root.getId() : null;
    name = root.getName();
    vcsName = root.getVcsName();

    final String ownerProjectId = root.getScope().getOwnerProjectId();
    final SProject projectById = dataProvider.getServer().getProjectManager().findProjectById(ownerProjectId);
    if (projectById != null) {
      project = new ProjectRef(projectById, apiUrlBuilder);
    } else {
      project = new ProjectRef(null, ownerProjectId, apiUrlBuilder);
    }

    properties = new Properties(root.getProperties());
    modificationCheckInterval = root.isUseDefaultModificationCheckInterval() ? null : root.getModificationCheckInterval();
    final VcsRootStatus rootStatus = dataProvider.getVcsManager().getStatus(root);
    status = rootStatus.getType().toString();
    lastChecked = Util.formatTime(rootStatus.getTimestamp());

    vcsRootInstances = new Href(VcsRootInstanceRequest.getVcsRootInstancesHref(root), apiUrlBuilder);
  }

  @NotNull
  public static UserParametersHolder getUserParametersHolder(@NotNull final SVcsRoot root) {
    //todo (TeamCity) open API: make VCS root UserParametersHolder
    return new UserParametersHolder() {
      public void addParameter(@NotNull final Parameter param) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.put(param.getName(), param.getValue());
        updateVCSRoot(root, newProperties, null);
      }

      public void removeParameter(@NotNull final String paramName) {
        final Map<String, String> newProperties = new HashMap<String, String>(root.getProperties());
        newProperties.remove(paramName);
        updateVCSRoot(root, newProperties, null);
      }

      @NotNull
      public Collection<Parameter> getParametersCollection() {
        final ArrayList<Parameter> result = new ArrayList<Parameter>();
        for (Map.Entry<String, String> item : getParameters().entrySet()) {
          result.add(new SimpleParameter(item.getKey(), item.getValue()));
        }
        return result;
      }

      @NotNull
      public Map<String, String> getParameters() {
        return root.getProperties();
      }
    };
  }

  public static void updateVCSRoot(@NotNull final SVcsRoot root,
                                   @Nullable final Map<String, String> newProperties,
                                   @Nullable final String newName) {
    try {
      if (newName != null) {
        root.setName(newName);
      }
      if (newProperties != null) {
        root.setProperties(newProperties);
      }
    } catch (ProjectNotFoundException e) {
      throw new NotFoundException("Could not find project for VCS root: " + root.getExternalId());
    }
  }

  public static String getFieldValue(final SVcsRoot vcsRoot, final String field, final DataProvider dataProvider) {
    if ("id".equals(field)) {
      return vcsRoot.getExternalId();
    } else if ("internalId".equals(field)) {
      return String.valueOf(vcsRoot.getId());
    } else if ("name".equals(field)) {
      return vcsRoot.getName();
    } else if ("vcsName".equals(field)) {
      return vcsRoot.getVcsName();
    } else if ("projectInternalId".equals(field)) { //Not documented, do we actually need this?
      return vcsRoot.getScope().getOwnerProjectId();
    } else if ("projectId".equals(field)) { //todo: do we actually need this?
      return dataProvider.getProjectByInternalId(vcsRoot.getScope().getOwnerProjectId()).getExternalId();
    } else if ("modificationCheckInterval".equals(field)) {
      return String.valueOf(vcsRoot.getModificationCheckInterval());
    } else if ("defaultModificationCheckIntervalInUse".equals(field)) { //Not documented
      return String.valueOf(vcsRoot.isUseDefaultModificationCheckInterval());
    } else if ("repositoryMappings".equals(field)) { //Not documented
      try {
        return String.valueOf(getRepositoryMappings(vcsRoot, dataProvider.getVcsManager()));
      } catch (VcsException e) {
        throw new InvalidStateException("Error retrieving mapping", e);
      }
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, name, vcsName, projectId, modificationCheckInterval");
  }

  public static void setFieldValue(@NotNull final SVcsRoot vcsRoot,
                                   @Nullable final String field,
                                   @Nullable final String newValue,
                                   @NotNull final DataProvider dataProvider,
                                   @NotNull final ProjectFinder projectFinder) {
    if ("name".equals(field)) {
      updateVCSRoot(vcsRoot, null, newValue);
      return;
    } else if ("modificationCheckInterval".equals(field)) {
      if ("".equals(newValue)) {
        vcsRoot.restoreDefaultModificationCheckInterval();
      } else {
        int newInterval = 0;
        try {
          newInterval = Integer.valueOf(newValue);
        } catch (NumberFormatException e) {
          throw new BadRequestException(
            "Field 'modificationCheckInterval' should be an integer value. Error during parsing: " + e.getMessage());
        }
        vcsRoot.setModificationCheckInterval(newInterval); //todo (TeamCity) open API can set negative value which gets applied
      }
      vcsRoot.persist();  //todo: (TeamCity) open API need to call persist or not ???
      return;
    } else if ("defaultModificationCheckIntervalInUse".equals(field)){
      boolean newUseDefault = Boolean.valueOf(newValue);
      if (newUseDefault) {
        vcsRoot.restoreDefaultModificationCheckInterval();
        vcsRoot.persist();  //todo: (TeamCity) open API need to call persist or not ???
        return;
      }
      throw new BadRequestException("Setting field 'defaultModificationCheckIntervalInUse' to false is not supported, set modificationCheckInterval instead.");
    } else if ("projectId".equals(field) || "project".equals(field)) { //project locator is acually supported, "projectId" is preserved for compatibility with previous versions
      SProject targetProject = projectFinder.getProject(newValue);
      dataProvider.getServer().getProjectManager().moveVcsRootToProject(vcsRoot, targetProject);
      return;
    }

    throw new NotFoundException("Setting field '" + field + "' is not supported. Supported are: name, project, modificationCheckInterval");
  }

  public static Collection<VcsMappingElement> getRepositoryMappings(@NotNull final SVcsRoot root, @NotNull final VcsManager vcsManager) throws VcsException {
    final VcsSettings vcsSettings = root.createVcsSettings(CheckoutRules.DEFAULT);
    final MappingGeneratorService mappingGenerator = vcsManager.getVcsService(vcsSettings, MappingGeneratorService.class);

    if (mappingGenerator == null) {
      return Collections.emptyList();
    }
    return mappingGenerator.generateMapping();
  }
}

