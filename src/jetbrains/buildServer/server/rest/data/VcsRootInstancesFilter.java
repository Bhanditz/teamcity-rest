package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class VcsRootInstancesFilter extends AbstractFilter<VcsRootInstance> {
  private static final Logger LOG = Logger.getInstance(VcsRootInstancesFilter.class.getName());
  public static final String VCS_ROOT_DIMENSION = "vcsRoot";

  @Nullable private final String myVcsType;
  @Nullable private final String myRepositoryIdString;
  @Nullable private final SProject myProject;
  @Nullable private final SVcsRoot myVcsRoot;
  @Nullable private final SBuildType myBuildType;

  @NotNull private VcsManager myVcsManager;

  public VcsRootInstancesFilter(@NotNull final Locator locator,
                                @NotNull ProjectFinder projectFinder,
                                @NotNull BuildTypeFinder buildTypeFinder,
                                @NotNull VcsRootFinder vcsRootFinder,
                                @NotNull VcsManager vcsManager) {
    super(locator.getSingleDimensionValueAsLong(PagerData.START), locator.getSingleDimensionValueAsLong(PagerData.COUNT) != null
                                                                  ? locator.getSingleDimensionValueAsLong(PagerData.COUNT).intValue()
                                                                  : null);
    myVcsManager = vcsManager;
    myVcsType = locator.getSingleDimensionValue("type");
    final String projectLocator = locator.getSingleDimensionValue("project"); //todo: what this project should mean?
    if (projectLocator != null) {
      myProject = projectFinder.getProject(projectLocator);
    } else {
      myProject = null;
    }
    final String buildTypeLocator = locator.getSingleDimensionValue("buildType"); //todo: what this project should mean?
    if (buildTypeLocator != null) {
      myBuildType = buildTypeFinder.getBuildType(myProject, buildTypeLocator);
    } else {
      myBuildType = null;
    }
    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT_DIMENSION);
    if (vcsRootLocator != null) {
      myVcsRoot = vcsRootFinder.getVcsRoot(vcsRootLocator);
    } else {
      myVcsRoot = null;
    }

    myRepositoryIdString = locator.getSingleDimensionValue("repositoryIdString");
  }

  @Override
  protected boolean isIncluded(@NotNull VcsRootInstance root) {
    if (myVcsType != null && !myVcsType.equals(root.getVcsName())) {
      return false;
    }
    if (myProject != null && !myProject.equals(root.getParent().getProject())) {
      return false;
    }
    if (myBuildType != null && !root.getUsages().keySet().contains(myBuildType)) { // todo: how to find usages in templates?
      return false;
    }
    if (myVcsRoot != null && !myVcsRoot.equals(root.getParent())) {
      return false;
    }
    if (myRepositoryIdString != null && !VcsRootsFilter.repositoryIdStringMatches(root, myRepositoryIdString, myVcsManager)) {
      return false;
    }

    return true;
  }
}