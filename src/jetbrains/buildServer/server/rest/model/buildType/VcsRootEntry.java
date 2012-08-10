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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.change.VcsRootRef;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "vcs-root-entry")
@XmlType(name = "vcs-root-entry")
public class VcsRootEntry {
  @XmlAttribute(name = "id")
  public String id;

  @XmlElement(name = "checkout-rules")
  public String checkoutRules;
  @XmlElement(name = "vcs-root")
  public VcsRootRef vcsRootRef;

  public VcsRootEntry() {
  }

  public VcsRootEntry(final @NotNull VcsRoot vcsRootParam,
                      @NotNull final CheckoutRules CheckoutRulesParam,
                      @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = String.valueOf(vcsRootParam.getId());
    vcsRootRef = new VcsRootRef(vcsRootParam, apiUrlBuilder);
    checkoutRules = CheckoutRulesParam.getAsString();
  }

  public VcsRootEntry(jetbrains.buildServer.vcs.VcsRootEntry entry, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = String.valueOf(entry.getVcsRoot().getId());
    vcsRootRef = new VcsRootRef(entry.getVcsRoot(), apiUrlBuilder);
    checkoutRules = entry.getCheckoutRules().getAsString();
  }
}
