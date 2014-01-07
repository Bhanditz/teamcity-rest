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

package jetbrains.buildServer.server.rest.model.project;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "projects")
@XmlType(name = "projects")
public class Projects {
  @XmlElement(name = "project")
  public List<Project> projects;

  public Projects() {
  }

  public Projects(List<SProject> projectObjects, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    projects = new ArrayList<Project>(projectObjects.size());
    for (SProject project : projectObjects) {
      projects.add(new Project(project, fields.getNestedField("project"), beanContext));
    }
  }

  @NotNull
  public List<SProject> getProjectsFromPosted(@NotNull ProjectFinder projectFinder) {
    if (projects == null) {
      throw new BadRequestException("List of projects should be supplied");
    }
    final ArrayList<SProject> result = new ArrayList<SProject>(projects.size());
    for (Project project : projects) {
      result.add(project.getProjectFromPosted(projectFinder));
    }
    return result;
  }
}