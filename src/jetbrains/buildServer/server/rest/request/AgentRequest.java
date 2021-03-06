/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import com.intellij.openapi.util.text.StringUtil;
import io.swagger.annotations.Api;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.*;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@Path(AgentRequest.API_AGENTS_URL)
@Api("Agent")
public class AgentRequest {
  @Context private DataProvider myDataProvider;
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private AgentPoolFinder myAgentPoolFinder;
  @Context @NotNull private AgentFinder myAgentFinder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_AGENTS_URL = Constants.API_URL + "/agents";

  public static String getHref() {
    return API_AGENTS_URL;
  }

  public static String getItemsHref(final String locatorText) {
    return API_AGENTS_URL + "?locator=" + locatorText;
  }

  public static String getAgentHref(@NotNull final SBuildAgent agent) {
    return API_AGENTS_URL + "/" + AgentFinder.getLocator(agent);
  }

  /**
   * Returns list of agents
   * @param includeDisconnected Deprecated, use "locator" parameter instead
   * @param includeUnauthorized Deprecated, use "locator" parameter instead
   * @param locator
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Agents serveAgents(@QueryParam("includeDisconnected") Boolean includeDisconnected,
                            @QueryParam("includeUnauthorized") Boolean includeUnauthorized,
                            @QueryParam("locator") String locator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    if (locator != null && includeDisconnected != null){
      throw new BadRequestException("Both 'includeDisconnected' URL parameter and '" + AgentFinder.CONNECTED + "' locator dimension are specified. Please use locator only.");
    }
    if (locator != null && includeUnauthorized != null){
      throw new BadRequestException("Both 'includeUnauthorized' URL parameter and '" + AgentFinder.AUTHORIZED + "' locator dimension are specified. Please use locator only.");
    }

    String locatorToUse = locator;
    if (includeDisconnected != null) {
      //pre-8.1 compatibility:
      locatorToUse = Locator.createEmptyLocator().setDimensionIfNotPresent(AgentFinder.CONNECTED, String.valueOf(!includeDisconnected)).getStringRepresentation();
    }

    final Locator parsedLocator = StringUtil.isEmpty(locatorToUse) ? Locator.createEmptyLocator() : new Locator(locatorToUse);
    if (includeUnauthorized != null) {
      //pre-8.1 compatibility:
      locatorToUse = parsedLocator.setDimensionIfNotPresent(AgentFinder.AUTHORIZED, String.valueOf(!includeUnauthorized)).getStringRepresentation();
    }

    final PagedSearchResult<SBuildAgent> result = myAgentFinder.getItems(locatorToUse);

    final PagerData pager = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorToUse, "locator");
    return new Agents(result.myEntries, pager,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentLocator}")
  @Produces({"application/xml", "application/json"})
  public Agent serveAgent(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    return new Agent(myAgentFinder.getItem(agentLocator), myAgentPoolFinder, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{agentLocator}")
  public void deleteAgent(@PathParam("agentLocator") String agentLocator) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    myServiceLocator.getSingletonService(BuildAgentManager.class).removeAgent(agent, myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser());
  }

  @GET
  @Path("/{agentLocator}/pool")
  @Produces({"application/xml", "application/json"})
  public AgentPool getAgentPool(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getAgentPool(agent);
    return agentPool == null ? null : new AgentPool(agentPool,  new Fields(fields),myBeanContext);
  }

  @PUT
  @Path("/{agentLocator}/pool")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public AgentPool setAgentPool(@PathParam("agentLocator") String agentLocator, AgentPool agentPool, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    myDataProvider.addAgentToPool(agentPool.getAgentPoolFromPosted(myAgentPoolFinder), agent.getAgentTypeId());
    final jetbrains.buildServer.serverSide.agentPools.AgentPool foundPool = myAgentPoolFinder.getAgentPool(agent);
    return new AgentPool(foundPool, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentLocator}/enabledInfo")
  @Produces({"application/xml", "application/json"})
  public AgentEnabledInfo getEnabledInfo(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    return new AgentEnabledInfo(agent, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{agentLocator}/enabledInfo")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public AgentEnabledInfo setEnabledInfo(@PathParam("agentLocator") String agentLocator, AgentEnabledInfo enabledInfo, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    if (enabledInfo == null) throw new BadRequestException("No data is sent as payload.");
    String commentText = enabledInfo.getCommentTextFromPosted();
    Boolean value = enabledInfo.getStatusFromPosted();
    if (value == null && commentText == null) throw new BadRequestException("Neither value nor comment are provided, nothing to change");
    Date switchTime = enabledInfo.getStatusSwitchTimeFromPosted(myServiceLocator);
    SUser currentUser = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    if (switchTime == null){
      agent.setEnabled(value != null ? value : agent.isEnabled(), currentUser, Agent.getActualActionComment(commentText));
    } else{
      agent.setEnabled(value != null ? value : agent.isEnabled(), currentUser, Agent.getActualActionComment(commentText), switchTime.getTime());
    }

    return new AgentEnabledInfo(agent, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentLocator}/authorizedInfo")
  @Produces({"application/xml", "application/json"})
  public AgentAuthorizedInfo getAuthorizedInfo(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    return new AgentAuthorizedInfo(agent.isAuthorized(), agent.getAuthorizeComment(), new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{agentLocator}/authorizedInfo")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public AgentAuthorizedInfo setAuthorizedInfo(@PathParam("agentLocator") String agentLocator, AgentAuthorizedInfo authorizedInfo, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    if (authorizedInfo == null) throw new BadRequestException("No data is sent as payload.");
    String commentText = authorizedInfo.getCommentTextFromPosted();
    Boolean value = authorizedInfo.getStatusFromPosted();
    if (value == null && commentText == null) throw new BadRequestException("Neither value nor comment are provided, nothing to change");
    agent.setAuthorized(value != null ? value : agent.isAuthorized(), myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser(), Agent.getActualActionComment(commentText));

    return new AgentAuthorizedInfo(agent.isAuthorized(), agent.getAuthorizeComment(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentLocator}/{field}")
  @Produces("text/plain")
  public String serveAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName) {
    return Agent.getFieldValue(myAgentFinder.getItem(agentLocator), fieldName, myServiceLocator);
  }

  /**
   * Experimental support to get currently compatible build types
   */
  @GET
  @Path("/{agentLocator}/compatibleBuildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes getCompatibleBuildTypes(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    if (!AuthUtil.canViewAgentDetails(myBeanContext.getServiceLocator().getSingletonService(SecurityContext.class).getAuthorityHolder(), agent)){
      throw new AuthorizationFailedException("No permission to view agent details");
    }
    Fields fieldsDefinition = new Fields(Agent.COMPATIBLE_BUILD_TYPES + "(" + (StringUtil.isEmpty(fields) ? "$long" : fields) + ")");
    return new Agent(agent, myAgentPoolFinder, fieldsDefinition, myBeanContext).compatibleBuildTypes;
  }

  /**
   * Experimental support to get currently incompatible build types with incompatibility reason
   */
  @GET
  @Path("/{agentLocator}/incompatibleBuildTypes")
  @Produces({"application/xml", "application/json"})
  public Compatibilities geIncompatibleBuildTypes(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    if (!AuthUtil.canViewAgentDetails(myBeanContext.getServiceLocator().getSingletonService(SecurityContext.class).getAuthorityHolder(), agent)){
      throw new AuthorizationFailedException("No permission to view agent details");
    }
    Fields fieldsDefinition = new Fields(Agent.INCOMPATIBLE_BUILD_TYPES + "(" + (StringUtil.isEmpty(fields) ? "$long" : fields) + ")");
    return new Agent(agent, myAgentPoolFinder, fieldsDefinition, myBeanContext).incompatibleBuildTypes;
  }

  /**
   * Experimental use only
   */
  @GET
  @Path("/{agentLocator}/compatibilityPolicy")
  @Produces({"application/xml", "application/json"})
  public CompatibilityPolicy getAllowedRunConfigurations(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    return CompatibilityPolicy.getCompatibilityPolicy(agent, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental use only
   */
  @PUT
  @Path("/{agentLocator}/compatibilityPolicy")
  @Produces({"application/xml", "application/json"})
  public CompatibilityPolicy setAllowedRunConfigurations(@PathParam("agentLocator") String agentLocator, CompatibilityPolicy payload, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    payload.applyTo(agent, myServiceLocator);
    return CompatibilityPolicy.getCompatibilityPolicy(agent, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{agentLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName, String value) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    Agent.setFieldValue(agent, fieldName, value, myServiceLocator);
    return Agent.getFieldValue(agent, fieldName, myServiceLocator);
  }

  public static AgentRequest createForTests(@NotNull final BeanContext beanContext) {
    AgentRequest result = new AgentRequest();
    result.myBeanContext = beanContext;
    result.myServiceLocator = beanContext.getServiceLocator();
    result.myAgentFinder = beanContext.getSingletonService(AgentFinder.class);
    result.myAgentPoolFinder = beanContext.getSingletonService(AgentPoolFinder.class);
    return result;
  }
}
