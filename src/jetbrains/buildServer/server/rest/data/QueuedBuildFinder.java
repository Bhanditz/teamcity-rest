package jetbrains.buildServer.server.rest.data;

import java.util.List;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.12.13
 */
public class QueuedBuildFinder extends AbstractFinder<SQueuedBuild> {
  private static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project";
  public static final String AGENT = "agent";
  public static final String PERSONAL = "personal";

  private final BuildQueue myBuildQueue;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final DataProvider myDataProvider;

  public QueuedBuildFinder(final BuildQueue buildQueue,
                           final ProjectFinder projectFinder,
                           final BuildTypeFinder buildTypeFinder,
                           final UserFinder userFinder,
                           final DataProvider dataProvider) {
    super(new String[]{DIMENSION_ID, PROJECT, BUILD_TYPE, AGENT, PERSONAL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myBuildQueue = buildQueue;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myDataProvider = dataProvider;
  }

  @NotNull
  @Override
  public List<SQueuedBuild> getAllItems() {
    return myBuildQueue.getItems();
  }

  @Override
  protected SQueuedBuild findSingleItem(@NotNull final Locator locator) {

    if (locator.isSingleValue()) {
     // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      locator.checkLocatorFullyProcessed();
      return getQueuedBuildByPromotionId(singleValueAsLong);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      locator.checkLocatorFullyProcessed();
      return getQueuedBuildByPromotionId(id);
    }

   return null;
  }

  private SQueuedBuild getQueuedBuildByPromotionId(final Long id) {
    final BuildPromotion buildPromotion = BuildFinder.getBuildPromotion(id, myDataProvider.getPromotionManager());
    final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild == null){
      throw new NotFoundException("No queued build can be found by id '" + buildPromotion.getId() + "' (while promotion exists).");
    }
    return queuedBuild;
  }

  @Override
  protected AbstractFilter<SQueuedBuild> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<SQueuedBuild> result =
      new MultiCheckerFilter<SQueuedBuild>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return internalProject.equals(item.getBuildType().getProject());
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(project, buildTypeLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return buildType.equals(item.getBuildPromotion().getParentBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final SBuildAgent agent = myDataProvider.getAgent(agentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return agent.equals(item.getBuildAgent());
        }
      });
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue("compatibleAgent"); //experimental
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = myDataProvider.getAgent(compatibleAagentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return item.getCompatibleAgents().contains(agent);
        }
      });
    }

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong("compatibleAgentsCount"); //experimental
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return compatibleAgentsCount.equals(Integer.valueOf(item.getCompatibleAgents().size()).longValue());
        }
      });
    }

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    return result;
  }

  /**
   * Returns build promotion if found. Othervise returns null. Throws no locator exceptions
   */
  @Nullable
  public BuildPromotion getBuildPromotionByBuildQueueLocator(@Nullable final String buildQueueLocator) {
    if (StringUtil.isEmpty(buildQueueLocator)) {
      return null;
    }

    final Locator locator = new Locator(buildQueueLocator);

    if (locator.isSingleValue()) { // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      return myDataProvider.getPromotionManager().findPromotionById(singleValueAsLong);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return myDataProvider.getPromotionManager().findPromotionById(id);
    }

    return null;
  }
}