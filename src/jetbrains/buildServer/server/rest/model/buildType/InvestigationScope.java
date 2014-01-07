package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.Problem;
import jetbrains.buildServer.server.rest.model.problem.Test;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
public class InvestigationScope {
  @XmlElement
  public String type; //todo: make this typed

  @XmlElement
  public BuildTypeRef buildType;

  /**
   * Experimental! will change in future versions.
   */
  @XmlElement
  public Test test;

  /**
   * Experimental! will change in future versions.
   */
  @XmlElement
  public Problem problem;

  @XmlElement
  public Project project;

   public InvestigationScope() {
  }

  public InvestigationScope(@NotNull final InvestigationWrapper investigation,
                            @NotNull final Fields fields,
                            @NotNull final ServiceLocator serviceLocator,
                            @NotNull final ApiUrlBuilder apiUrlBuilder) {
    type = investigation.getType();
    final BeanContext beanContext = new BeanContext(serviceLocator.getSingletonService(BeanFactory.class), serviceLocator, apiUrlBuilder);
    if (investigation.isBuildType()) {
      //noinspection ConstantConditions
      buildType = new BuildTypeRef((SBuildType)investigation.getBuildTypeRE().getBuildType(), serviceLocator, apiUrlBuilder);  //TeamCity open API issue: cast
    } else if (investigation.isTest()) {
      @SuppressWarnings("ConstantConditions") @NotNull final TestNameResponsibilityEntry testRE = investigation.getTestRE();

      final STest foundTest = serviceLocator.getSingletonService(TestFinder.class).findTest(testRE.getTestNameId());
      if (foundTest == null){
        throw new InvalidStateException("Cannot find test for investigation. Test name id: '" + testRE.getTestNameId() + "'.");
      }
      test = new Test(foundTest, beanContext, fields.getNestedField("test"));

      project = new Project((SProject)testRE.getProject(), fields.getNestedField("project"), beanContext); //TeamCity open API issue: cast
    } else if (investigation.isProblem()) {
      final BuildProblemResponsibilityEntry problemRE = investigation.getProblemRE();
      //noinspection ConstantConditions
      problem = new Problem(new ProblemWrapper(problemRE.getBuildProblemInfo().getId(), serviceLocator), serviceLocator, apiUrlBuilder, fields.getNestedField("problem"));
      project = new Project((SProject)problemRE.getProject(), fields.getNestedField("project"), beanContext); //TeamCity open API issue: cast
    } else {
      throw new InvalidStateException("Investigation wrapper type is not supported");
    }
  }
}
