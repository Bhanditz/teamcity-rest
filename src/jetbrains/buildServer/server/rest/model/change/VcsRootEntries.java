/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04.08.2009
 */
@XmlRootElement(name = "vcs-roots-entries")
public class VcsRootEntries {
  @XmlElement(name = "vcs-root-entry")
  public List<VcsRootEntry> vcsRootAssignments;

  public VcsRootEntries() {
  }

  public VcsRootEntries(final Collection<jetbrains.buildServer.vcs.VcsRootEntry> vcsRootEntries,
                        @NotNull final ApiUrlBuilder apiUrlBuilder) {
    vcsRootAssignments = new ArrayList<VcsRootEntry>(vcsRootEntries.size());
    for (jetbrains.buildServer.vcs.VcsRootEntry entry : vcsRootEntries) {
      vcsRootAssignments.add(new VcsRootEntry(entry, apiUrlBuilder));
    }
  }

}