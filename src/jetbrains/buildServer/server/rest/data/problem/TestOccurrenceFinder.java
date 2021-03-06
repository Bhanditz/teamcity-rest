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

package jetbrains.buildServer.server.rest.data.problem;

import com.google.common.collect.ComparisonChain;
import java.util.*;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
public class TestOccurrenceFinder extends AbstractFinder<STestRun> {
  private static final String BUILD = "build";
  private static final String TEST = "test";
  private static final String BUILD_TYPE = "buildType";
  public static final String AFFECTED_PROJECT = "affectedProject";
  private static final String CURRENT = "currentlyFailing";
  private static final String STATUS = "status";
  private static final String BRANCH = "branch";
  private static final String IGNORED = "ignored";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String MUTED = "muted";
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  protected static final String EXPAND_INVOCATIONS = "expandInvocations"; //experimental
  protected static final String INVOCATIONS = "invocations"; //experimental

  @NotNull private final TestFinder myTestFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildHistoryEx myBuildHistory;
  @NotNull private final CurrentProblemsManager myCurrentProblemsManager;

  public TestOccurrenceFinder(final @NotNull TestFinder testFinder,
                              final @NotNull BuildFinder buildFinder,
                              final @NotNull BuildTypeFinder buildTypeFinder,
                              final @NotNull ProjectFinder projectFinder,
                              final @NotNull BuildHistoryEx buildHistory,
                              final @NotNull CurrentProblemsManager currentProblemsManager) {
    super(DIMENSION_ID, TEST, BUILD_TYPE, BUILD, AFFECTED_PROJECT, CURRENT, STATUS, BRANCH, IGNORED, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED);
    setHiddenDimensions(EXPAND_INVOCATIONS, INVOCATIONS);
    myTestFinder = testFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myBuildHistory = buildHistory;
    myCurrentProblemsManager = currentProblemsManager;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final STestRun sTestRun) {
    return TestOccurrenceFinder.getTestRunLocator(sTestRun);
  }

  public static String getTestRunLocator(final @NotNull STestRun testRun) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(testRun.getTestRunId())).
      setDimension(BUILD, BuildRequest.getBuildLocator(testRun.getBuild())).getStringRepresentation();
  }

  public static String getTestInvocationsLocator(final @NotNull STestRun testRun) {
    return Locator.createEmptyLocator()
                  .setDimension(TEST, TestFinder.getTestLocator(testRun.getTest()))
                  .setDimension(BUILD, BuildRequest.getBuildLocator(testRun.getBuild()))
                  .setDimension(EXPAND_INVOCATIONS, Locator.BOOLEAN_TRUE)
                  .getStringRepresentation();
  }

  public static String getTestRunLocator(final @NotNull STest test) {
    return Locator.createEmptyLocator().setDimension(TEST, TestFinder.getTestLocator(test)).getStringRepresentation();
  }

  public static String getTestRunLocator(final @NotNull SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  @NotNull
  public TreeSet<STestRun> createContainerSet() {
    return new TreeSet<>(new Comparator<STestRun>() {
      @Override
      public int compare(final STestRun o1, final STestRun o2) {
        return ComparisonChain.start()
                              .compare(o1.getBuildId(), o2.getBuildId())
                              .compare(o1.getTestRunId(), o2.getTestRunId())
                              .result();
      }
    });
  }

  @Override
  @Nullable
  public STestRun findSingleItem(@NotNull final Locator locator) {
    /*
    if (locator.isSingleValue()) {
      Long idDimension = locator.getSingleValueAsLong();
      if (idDimension != null) {
        STestRun item = findTestByTestRunId(idDimension);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found.");
      }
    }
    */

    // dimension-specific item search

    Long idDimension = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (idDimension != null) {

      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
        if (builds.size() != 1) {
          return null;
        }
        SBuild build = builds.get(0).getAssociatedBuild();
        if (build == null) {
          throw new NotFoundException("No running/finished build found by locator '" + buildDimension + "'");
        }
        STestRun item = findTestByTestRunId(idDimension, build);
        if (item != null) {
          if ((long)item.getTestRunId() == idDimension) {
            return processInvocationExpansion(item, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
          }
          if (!(item instanceof MultiTestRun)) return null;
          for (STestRun testRun : getInvocations(item)) {
            if ((long)testRun.getTestRunId() == idDimension) {
              return processInvocationExpansion(testRun, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
            }
          }
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found in build with id " + build.getBuildId());
      }
      throw new BadRequestException("Cannot find test by " + DIMENSION_ID + " only, make sure to specify " + BUILD + " locator.");
    }

    return null;
  }

  @NotNull
  @Override
  public ItemHolder<STestRun> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;

      String testDimension = locator.getSingleDimensionValue(TEST);
      if (testDimension == null) {
        AggregatingItemHolder<STestRun> result = new AggregatingItemHolder<>();
        for (BuildPromotion build : builds) {
          SBuild associatedBuild = build.getAssociatedBuild();
          if (associatedBuild != null) {
            result.add(getPossibleExpandedTestsHolder(getBuildStatistics(associatedBuild).getAllTests(), locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS)));
          }
        }
        return result;
      }

      final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);
      final ArrayList<STestRun> result = new ArrayList<STestRun>();
      for (BuildPromotion build : builds) {
        SBuild associatedBuild = build.getAssociatedBuild();
        if (associatedBuild != null) {
          for (STest test : tests.myEntries) {
            STestRun item = findTest(test.getTestNameId(), associatedBuild);
            if (item != null) {
              result.add(item);
            }
          }
        }
      }
      return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    String testDimension = locator.getSingleDimensionValue(TEST);
    if (testDimension != null) {
      final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);

      String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeDimension != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
        final ArrayList<STestRun> result = new ArrayList<STestRun>();
        for (STest test : tests.myEntries) {
          result.addAll(myBuildHistory.getTestHistory(test.getTestNameId(), buildType.getBuildTypeId(), 0, getBranch(locator))); //no personal builds
        }
        return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
      }

      final ArrayList<STestRun> result = new ArrayList<STestRun>();
      final SProject affectedProject = getAffectedProject(locator);
      for (STest test : tests.myEntries) {
        result.addAll(myBuildHistory.getTestHistory(test.getTestNameId(), affectedProject, 0, getBranch(locator))); //no personal builds
      }
      return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      return getPossibleExpandedTestsHolder(getCurrentOccurrences(getAffectedProject(locator), myCurrentProblemsManager),
                                            locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      final SProject affectedProject = getAffectedProject(locator);
      final Set<STest> currentlyMutedTests = myTestFinder.getCurrentlyMutedTests(affectedProject);
      final ArrayList<STestRun> result = new ArrayList<STestRun>();
      for (STest test : currentlyMutedTests) {
        result.addAll(myBuildHistory.getTestHistory(test.getTestNameId(), affectedProject, 0, getBranch(locator)));  //no personal builds
      }
      return getPossibleExpandedTestsHolder(result, locator.getSingleDimensionValueAsBoolean(EXPAND_INVOCATIONS));
    }

    ArrayList<String> exampleLocators = new ArrayList<String>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(BUILD, "XXX"));
    exampleLocators.add(Locator.getStringLocator(TEST, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Unsupported test occurrence locator '" + locator.getStringRepresentation() + "'. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
  }

  @NotNull
  private STestRun processInvocationExpansion(@NotNull final STestRun item, @Nullable final Boolean expandInvocations) {
    if (expandInvocations == null || !expandInvocations) {
      return item;
    }
    return getInvocations(item).iterator().next();
  }

  @NotNull
  private Collection<STestRun> getInvocations(@NotNull final STestRun item) {
    if (!(item instanceof MultiTestRun)) return Collections.singletonList(item);
    MultiTestRun compositeRun = (MultiTestRun)item;
    return compositeRun.getTestRuns();
  }

  @NotNull
  private ItemHolder<STestRun> getPossibleExpandedTestsHolder(@NotNull final Iterable<STestRun> tests, @Nullable final Boolean expandInvocations) {
    if (expandInvocations == null || !expandInvocations) {
      return getItemHolder(tests);
    }
    return new ItemHolder<STestRun>() {
      @Override
      public void process(@NotNull final ItemProcessor<STestRun> processor) {
        for (STestRun entry : tests) {
          if (!(entry instanceof MultiTestRun)) {
            processor.processItem(entry);
          } else {
            for (STestRun nestedTestRun : getInvocations(entry)) {
              processor.processItem(nestedTestRun);
            }
          }
        }
      }
    };
  }

  @Nullable
  private String getBranch(@NotNull final Locator locator) {
    return locator.getSingleDimensionValue(BRANCH);
  }

  @NotNull
  private SProject getAffectedProject(@NotNull final Locator locator) {
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      return myProjectFinder.getItem(affectedProjectDimension);
    }else{
      return myProjectFinder.getRootProject();
    }
  }

  protected static final Comparator<STestRun> TEST_RUN_COMPARATOR = new Comparator<STestRun>() {
    public int compare(STestRun o1, STestRun o2) {
      // see also STestRun.NEW_FIRST_NAME_COMPARATOR

      // New failure goes first
      boolean isNew1 = o1.isNewFailure();
      boolean isNew2 = o2.isNewFailure();
      if (isNew1 && !isNew2) { return -1; }
      if (!isNew1 && isNew2) { return 1; }

      final TestGroupName grp1 = o1.getTest().getName().getGroupName();
      final TestGroupName grp2 = o2.getTest().getName().getGroupName();
      final int grpCompare = grp1.compareTo(grp2);
      if (grpCompare != 0) return grpCompare;

      final String name1 = o1.getTest().getName().getAsString();
      final String name2 = o2.getTest().getName().getAsString();
      final int nameCompare = name1.compareTo(name2);
      if (nameCompare != 0) return nameCompare;

      // Failure goes first
      boolean isFailed1 = o1.getStatus().isFailed();
      boolean isFailed2 = o2.getStatus().isFailed();
      if (isFailed1 && !isFailed2) { return -1; }
      if (!isFailed1 && isFailed2) { return 1; }

      // that's what STestRun.NEW_FIRST_NAME_COMPARATOR does not compare
      SBuild build1 = o1.getBuild();
      SBuild build2 = o2.getBuild();

      int datesComparison = build1.getServerStartDate().compareTo(build2.getServerStartDate());
      if (datesComparison != 0 ) return datesComparison;

      if (build1.getBuildId() != build2.getBuildId()) {
        return (int)(build1.getBuildId() - build2.getBuildId());
      }

      return o1.getOrderId() - o2.getOrderId();
    }
  };

  @NotNull
  public static Set<STestRun> getCurrentOccurrences(@NotNull final SProject affectedProject, @NotNull final CurrentProblemsManager currentProblemsManager) {
    final CurrentProblems currentProblems = currentProblemsManager.getProblemsForProject(affectedProject);
    final Map<TestName, List<STestRun>> failingTests = currentProblems.getFailingTests();
    final Map<TestName, List<STestRun>> mutedTestFailures = currentProblems.getMutedTestFailures();
    final TreeSet<STestRun> result = new TreeSet<STestRun>(TEST_RUN_COMPARATOR);
    //todo: check whether STestRun is OK to put into the set
    for (List<STestRun> testRuns : failingTests.values()) {
      result.addAll(testRuns);
    }
    for (List<STestRun> testRuns : mutedTestFailures.values()) {
      result.addAll(testRuns);
    }
    return result;
  }

  @NotNull
  @Override
  public ItemFilter<STestRun> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<STestRun> result = new MultiCheckerFilter<STestRun>();

    if (locator.isUnused(DIMENSION_ID)){
      Long testRunId = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
      if (testRunId != null) {
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            return testRunId.intValue() == item.getTestRunId();
          }
        });
      }
    }

    final String statusDimension = locator.getSingleDimensionValue(STATUS);
    if (statusDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return statusDimension.equals(item.getStatus().getText()); //todo: support $help here
        }
      });
    }

    final Boolean ignoredDimension = locator.getSingleDimensionValueAsBoolean(IGNORED);
    if (ignoredDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return FilterUtil.isIncludedByBooleanFilter(ignoredDimension, item.isIgnored());
        }
      });
    }

    if (locator.getUnusedDimensions().contains(TEST)) {
      String testDimension = locator.getSingleDimensionValue(TEST);
      if (testDimension != null) {
        final PagedSearchResult<STest> tests = myTestFinder.getItems(testDimension);
        final HashSet<Long> testNameIds = new HashSet<Long>();
        for (STest test : tests.myEntries) {
          testNameIds.add(test.getTestNameId());
        }
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            return testNameIds.contains(item.getTest().getTestNameId());
          }
        });
      }
    }

    if (locator.isUnused(BUILD)) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        List<BuildPromotion> builds = myBuildFinder.getBuilds(null, buildDimension).myEntries;
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            return builds.contains(item.getBuild().getBuildPromotion());
          }
        });
      }
    }

    if (locator.isUnused(BUILD_TYPE)) {
      String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeDimension != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension, false);
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            return item.getBuild().getBuildTypeId().equals(buildType.getInternalId());
          }
        });
      }
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, isCurrentlyInvestigated(item));
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) { //it is important to filter even if prefiltered items processed the tests as that does not consider mute scope
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) { //todo: TeamCity API (MP): is there an API way to figure out there is a mute for a STestRun ?
          //todo: check mute in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, isCurrentlyMuted(item));
        }
      });
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return FilterUtil.isIncludedByBooleanFilter(muteDimension, item.isMuted());
        }
      });
    }

    if (locator.getUnusedDimensions().contains(CURRENT)) {
      final String currentDimension = locator.getSingleDimensionValue(CURRENT);
      if (currentDimension != null) {
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            return !item.isFixed(); //todo: is this the same as the test occurring in current problems???
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(INVOCATIONS)) {
      final String dimensionValue = locator.getSingleDimensionValue(INVOCATIONS);
      if (dimensionValue != null) {
        result.add(new FilterConditionChecker<STestRun>() {
          public boolean isIncluded(@NotNull final STestRun item) {
            Collection<STestRun> testRuns = getInvocations(item);
            FinderSearchMatcher<STestRun> matcher =
              new FinderSearchMatcher<>(dimensionValue, new DelegatingAbstractFinder<STestRun>(TestOccurrenceFinder.this) {
                @Nullable
                @Override
                public STestRun findSingleItem(@NotNull final Locator locator) {
                  return null;
                }

                @NotNull
                @Override
                public ItemHolder getPrefilteredItems(@NotNull final Locator locator) {
                  return getItemHolder(testRuns);
                }
              });
            return matcher.matches(null);
          }
        });
      }
    }

    return result;
  }

  public boolean isCurrentlyMuted(@NotNull final STestRun item) {  //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final CurrentMuteInfo currentMuteInfo = item.getTest().getCurrentMuteInfo();
    if (currentMuteInfo == null){
      return false;
    }
    final SBuildType buildType = item.getBuild().getBuildType();
    if (buildType == null){
      return false; //might need to log this
    }

    if (currentMuteInfo.getBuildTypeMuteInfo().keySet().contains(buildType)) return true;

    final Set<SProject> projects = currentMuteInfo.getProjectsMuteInfo().keySet();
    return ProjectFinder.isSameOrParent(projects, buildType.getProject());
  }

  public boolean isCurrentlyInvestigated(@NotNull final STestRun item) {  //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final List<TestNameResponsibilityEntry> testResponsibilities =
      item.getTest().getAllResponsibilities();//todo: TeamCity API (MP): what is the difference with getResponsibility() ?
    for (TestNameResponsibilityEntry testResponsibility : testResponsibilities) {
      final SBuildType buildType = item.getBuild().getBuildType();
      if (buildType != null) {  //might need to log this
        if (ProjectFinder.isSameOrParent(testResponsibility.getProject(), buildType.getProject())) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static BuildStatistics getBuildStatistics(@NotNull final SBuild build) {
    //  This is different from build.getFullStatistics() in the following ways:
    //  - stacktrace are not pre-loaded (loads all them into memory), but will be retrieved in a lazy fashion
    //  - compilation errors are not loaded (not necessary)

    //ideally, need to check what will be used in the response and request only those details
    BuildStatisticsOptions options = new BuildStatisticsOptions(
      BuildStatisticsOptions.PASSED_TESTS
      | BuildStatisticsOptions.IGNORED_TESTS
      | BuildStatisticsOptions.FIRST_FAILED_IN_BUILD
      | BuildStatisticsOptions.FIXED_IN_BUILD
      , 0);
    return build.getBuildStatistics(options);
  }

  @Nullable
  private STestRun findTest(final @NotNull Long testNameId, final @NotNull SBuild build) {
    return build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestNameId(testNameId);
  }

  @Nullable
  private STestRun findTestByTestRunId(@NotNull final Long testRunId, @NotNull final SBuild build) {
    //todo: TeamCity API (MP) how to implement this without build?
    //todo: TeamCity API: if stacktraces are not loaded,should I then load them somehow to get them for the returned STestRun (see TestOccurrence)
    return build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestRunId(testRunId);
  }

  private class DelegatingAbstractFinder<T> extends AbstractFinder<T> {
    private final AbstractFinder<T> myDelegate;

    public DelegatingAbstractFinder(final AbstractFinder<T> delegate) {
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public String[] getKnownDimensions() {
      return myDelegate.getKnownDimensions();
    }

    @NotNull
    @Override
    public String[] getHiddenDimensions() {
      return myDelegate.getHiddenDimensions();
    }

    @Nullable
    @Override
    public Locator.DescriptionProvider getLocatorDescriptionProvider() {
      return myDelegate.getLocatorDescriptionProvider();
    }

    @Nullable
    @Override
    public T findSingleItem(@NotNull final Locator locator) {
      return myDelegate.findSingleItem(locator);
    }

    @NotNull
    @Override
    public ItemHolder getPrefilteredItems(@NotNull final Locator locator) {
      return myDelegate.getPrefilteredItems(locator);
    }

    @NotNull
    @Override
    public ItemFilter getFilter(@NotNull final Locator locator) {
      return myDelegate.getFilter(locator);
    }

    @NotNull
    @Override
    public String getItemLocator(@NotNull final T item) {
      return myDelegate.getItemLocator(item);
    }

    @Nullable
    @Override
    public Set<T> createContainerSet() {
      return myDelegate.createContainerSet();
    }
  }
}
