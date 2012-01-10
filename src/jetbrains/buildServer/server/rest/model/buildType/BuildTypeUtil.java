/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Field;
import java.util.HashMap;
import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
public class BuildTypeUtil {
  private static final Logger LOG = Logger.getInstance(BuildTypeUtil.class.getName());

  public static HashMap<String, String> getSettingsParameters(final SBuildType buildType) {
    HashMap<String, String> properties = new HashMap<String, String>();
    addAllOptionsAsProperties(properties, buildType);
    //todo: is the right way to do?
    properties.put("artifactRules", buildType.getArtifactPaths());
    properties.put("checkoutDirectory", buildType.getCheckoutDirectory());
    properties.put("checkoutMode", buildType.getCheckoutType().name());
    properties.put("buildNumberCounter", (new Long(buildType.getBuildNumbers().getBuildCounter())).toString());
    return properties;
  }

  /**
   * Caller must ensure 'name' is a valid name of a BuildType setting
   * @see #getSettingsParameters(jetbrains.buildServer.serverSide.SBuildType)
   */
  public static void setSettingsParameter(final SBuildType buildType, final String name, final String value) {
    if ("artifactRules".equals(name)) {
      buildType.setArtifactPaths(value);
    } else if ("checkoutDirectory".equals(name)) {
      buildType.setCheckoutDirectory(value);
    } else if ("checkoutMode".equals(name)) {
      buildType.setCheckoutType(BuildTypeDescriptor.CheckoutType.valueOf(value));
    } else if ("buildNumberCounter".equals(name)) {
      buildType.getBuildNumbers().setBuildNumberCounter(new Long(value));
    } else {
      final Option option = Option.fromKey(name);
      if (option == null) {
        throw new IllegalArgumentException("No Build Type option found for name '" + name + "'");
      }
      final Object optionValue = option.fromString(value);
      //noinspection unchecked
      buildType.setOption(option, optionValue);
    }
  }

  //todo: might use a generic util for this (e.g. Static HTML plugin has alike code to get all Page Places)
  private static void addAllOptionsAsProperties(final HashMap<String, String> properties, final SBuildType buildType) {
    Field[] declaredFields = BuildTypeOptions.class.getDeclaredFields();
    for (Field declaredField : declaredFields) {
      try {
        if (Option.class.isAssignableFrom(declaredField.get(buildType).getClass())) {
          Option option = null;
          option = (Option)declaredField.get(buildType);
          //noinspection unchecked
          properties.put(option.getKey(), buildType.getOption(option).toString());
        }
      } catch (IllegalAccessException e) {
        LOG.error("Error retrieving options of build configuration " + LogUtil.describe(buildType) + ", error: " + e.getMessage());
      }
    }
  }

  public static SBuildFeatureDescriptor getBuildTypeFeature(final SBuildType buildType, @NotNull final String featureId) {
    if (StringUtil.isEmpty(featureId)){
      throw new BadRequestException("Feature Id cannot be empty.");
    }
    SBuildFeatureDescriptor feature = CollectionsUtil.findFirst(buildType.getBuildFeatures(), new Filter<SBuildFeatureDescriptor>() {
      public boolean accept(@NotNull final SBuildFeatureDescriptor data) {
        return data.getId().equals(featureId);
      }
    });
    if (feature == null) {
      throw new NotFoundException("No feature with id '" + featureId + "' is found in the build configuration.");
    }
    return feature;
  }

  // see also VCSRootRequest.getProjectLocator
  public static SVcsRoot getVcsRoot(final VcsRootEntryDescription description, DataProvider dataProvider) {
    if (!StringUtil.isEmpty(description.vcsRootLocator)){
      if (description.vcsRootRef != null){
        throw new BadRequestException("Only one from vcsRootLocator attribute and vcs-root element should be specified.");
      }
      return dataProvider.getVcsRoot(description.vcsRootLocator); 
    }else{
      if (description.vcsRootRef == null){
        throw new BadRequestException("Either vcsRootLocator attribute or vcs-root element should be specified.");
      }
      final String vcsRootHref = description.vcsRootRef.href;
      if (StringUtil.isEmpty(vcsRootHref)){
        throw new BadRequestException("vcs-root element should have valid href attribute.");
      }
      return dataProvider.getVcsRoot(getLastPathPart(vcsRootHref));
    }
  }

  public static String getLastPathPart(final String vcsRootHref) {
    return StringUtil.lastPartOf(vcsRootHref, '/');
  }
}